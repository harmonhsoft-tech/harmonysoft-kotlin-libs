package tech.harmonysoft.oss.mongo.service

import com.mongodb.BasicDBObject
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Projections
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import java.util.concurrent.TimeUnit
import javax.inject.Named
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.slf4j.Logger
import tech.harmonysoft.oss.common.ProcessingResult
import tech.harmonysoft.oss.common.collection.MapUtil
import tech.harmonysoft.oss.common.data.DataProviderStrategy
import tech.harmonysoft.oss.common.util.ObjectUtil
import tech.harmonysoft.oss.mongo.config.TestMongoConfig
import tech.harmonysoft.oss.mongo.config.TestMongoConfigProvider
import tech.harmonysoft.oss.mongo.constant.Mongo
import tech.harmonysoft.oss.mongo.fixture.MongoTestFixture
import tech.harmonysoft.oss.test.binding.DynamicBindingContext
import tech.harmonysoft.oss.test.input.CommonTestInputHelper
import tech.harmonysoft.oss.test.util.VerificationUtil

@Named
class TestMongoManager(
    private val configProvider: TestMongoConfigProvider,
    private val inputHelper: CommonTestInputHelper,
    private val bindingContext: DynamicBindingContext,
    private val logger: Logger
) {

    private val allDocumentsFilter = BasicDBObject()

    val client: MongoClient by lazy {
        getClient(configProvider.data)
    }

    @AfterEach
    fun cleanUpData() {
        val db = client.getDatabase(configProvider.data.db)
        for (collectionName in db.listCollectionNames()) {
            logger.info("Deleting all documents from mongo collection {}", collectionName)
            val result = db.getCollection(collectionName).deleteMany(allDocumentsFilter)
            logger.info("Deleted {} document(s) in mongo collection {}", result.deletedCount, collectionName)
        }
    }

    fun getClient(config: TestMongoConfig): MongoClient {
        val auth = config.credential?.let {
            "${it.login}:${it.password}@"
        } ?: ""
        val connectionString = "mongodb://$auth${config.host}:${config.port}/${config.db}"
        val timeoutMs = 100
        val settings = MongoClientSettings
            .builder()
            .applyToSocketSettings {
                it.connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                it.readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            }
            .applyToClusterSettings {
                it.serverSelectionTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            }
            .applyConnectionString(ConnectionString(connectionString))
            .build()
        return MongoClients.create(settings)
    }

    /**
     * Executes [ensureDocumentExists] for every given document.
     */
    fun ensureDocumentsExist(collection: String, documents: Collection<Map<String, String>>) {
        for (document in documents) {
            ensureDocumentExists(collection, document)
        }
    }

    /**
     * Checks if target collection contains a document with given data and inserts it in case of absence
     */
    fun ensureDocumentExists(collection: String, data: Map<String, String>) {
        val record = inputHelper.parse(MongoTestFixture.TYPE, Unit, data)
        val filter = BasicDBObject().apply {
            for ((key, value) in record.data) {
                this[key] = value
            }
        }
        client.getDatabase(configProvider.data.db).getCollection(collection).updateOne(
            filter,
            Updates.set("dummy", "dummy"),
            UpdateOptions().upsert(true)
        )
        verifyDocumentsExist(collection, listOf(data))
    }

    fun verifyDocumentsExist(collectionName: String, input: List<Map<String, String>>) {
        val records = inputHelper.parse(MongoTestFixture.TYPE, Unit, input)
        val projection = records.flatMap { it.data.keys + it.toBind.keys }.toSet().toList()
        VerificationUtil.verifyConditionHappens {
            val collection = client.getDatabase(configProvider.data.db).getCollection(collectionName)
            val documents = collection
                .find(Mongo.Filter.ALL)
                .projection(Projections.include(projection))
                .toList()
                .map { MapUtil.flatten(it) }

            for (record in records) {
                val result = VerificationUtil.find(
                    expected = record.data,
                    candidates = documents,
                    keys = record.data.keys,
                    retrievalStrategy = DataProviderStrategy.fromMap(),
                    equalityChecker = { _, o1, o2 ->
                        ObjectUtil.areEqual(normalizeValue(o1), normalizeValue(o2))
                    }
                )
                if (!result.success) {
                    return@verifyConditionHappens result.mapError()
                }
                val matched = result.successValue
                for ((column, key) in record.toBind) {
                    bindingContext.storeBinding(key, normalizeValue(matched[column]))
                }
            }
            ProcessingResult.success()
        }
    }

    fun normalizeValue(v: Any?): Any? {
        return v?.let {
            when (it) {
                is ObjectId -> it.toString()
                else -> it
            }
        }
    }
}