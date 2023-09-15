package tech.harmonysoft.oss.cucumber.glue

import io.cucumber.datatable.DataTable
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import javax.inject.Inject
import tech.harmonysoft.oss.mongo.service.TestMongoManager

class MongoStepDefinitions {

    @Inject private lateinit var mongo: TestMongoManager

    @After
    fun cleanUpData() {
        mongo.cleanUpData()
    }

    @Given("^mongo ([^\\s]+) collection has the following documents?:$")
    fun ensureDocumentsExist(collection: String, data: DataTable) {
        mongo.ensureDocumentsExist(collection, data.asMaps())
    }

    @Given("^mongo ([^\\s]+) collection has the following JSON document:$")
    fun ensureJsonDocumentExists(collection: String, data: String) {
        mongo.ensureJsonDocumentExist(collection, data)
    }

    @Then("^mongo ([^\\s]+) collection should have the following documents?:$")
    fun verifyDocumentsExist(collectionName: String, data: DataTable) {
        mongo.verifyDocumentsExist(collectionName, data.asMaps())
    }

    @Then("^mongo ([^\\s]+) collection should have a document with at least the following data:$")
    fun verifyJsonDocumentExists(collectionName: String, json: String) {
        mongo.verifyJsonDocumentExists(collectionName, json)
    }
}