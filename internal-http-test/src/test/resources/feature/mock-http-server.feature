Feature: Mock HTTP server tests

  Scenario: Overwriting parameterless HTTP stub

    Given the following HTTP request is received by mock server:
      | method | path  |
      | GET    | /test |

    And the following mock HTTP response is returned:
      """
      first
      """

    And the following HTTP request is received by mock server:
      | method | path  |
      | GET    | /test |

    And the following mock HTTP response is returned:
      """
      second
      """

    When HTTP GET request to /test is made

    Then last HTTP GET request returns the following:
      """
      second
      """

  Scenario: Overwriting conditional HTTP stub

    Given the following HTTP request is received by mock server:
      | method | path  |
      | POST   | /test |

    And mock HTTP request body is a JSON with the following values:
      | query                |
      | <has-text:condition> |

    And the following mock HTTP response is returned:
      """
      first
      """

    And the following HTTP request is received by mock server:
      | method | path  |
      | POST   | /test |

    And mock HTTP request body is a JSON with the following values:
      | query                |
      | <has-text:condition> |

    And the following mock HTTP response is returned:
      """
      second
      """

    When HTTP POST request to /test is made with JSON body:
      """
      {
        "query": "condition"
      }
      """

    Then last HTTP POST request returns the following:
      """
      second
      """

  Scenario: Plain and conditional HTTP stubs to the same path

    Given the following HTTP request is received by mock server:
      | method | path  |
      | GET    | /test |

    And the following mock HTTP response is returned:
      """
      first
      """

    And the following HTTP request is received by mock server:
      | method | path  |
      | GET    | /test |

    And mock HTTP request has the following query parameter:
      | p1 |
      | v1 |

    And the following mock HTTP response is returned:
      """
      second
      """

    When HTTP GET request to /test is made

    Then last HTTP GET request returns the following:
      """
      first
      """

    And HTTP GET request to /test?p1=v2 is made

    And last HTTP GET request returns the following:
      """
      first
      """

    And HTTP GET request to /test?p1=v1 is made

    And last HTTP GET request returns the following:
      """
      second
      """

  Scenario: Non-overwriting conditional HTTP stubs

    Given the following HTTP request is received by mock server:
      | method | path  |
      | GET    | /test |

    And mock HTTP request has the following query parameter:
      | p1 |
      | v1 |

    And the following mock HTTP response is returned:
      """
      first
      """

    And the following HTTP request is received by mock server:
      | method | path  |
      | GET    | /test |

    And mock HTTP request has the following query parameter:
      | p1 |
      | v1 |

    And mock HTTP request has the following query parameter:
      | p2 |
      | v2 |

    And the following mock HTTP response is returned:
      """
      second
      """

    When HTTP GET request to /test?p1=v1 is made

    Then last HTTP GET request returns the following:
      """
      first
      """

    And HTTP GET request to /test?p1=v1&p2=v2 is made

    And last HTTP GET request returns the following:
      """
      second
      """

  Scenario: Partial JSON call verification

    Given dynamic key path is bound to value 'one/two'

    When HTTP POST request to /context/one/two/three is made with JSON body:
      """
      {
        "key1": "value1",
        "key2": "value2",
        "key3": "value3"
      }
      """

    Then the following POST request for path /context/<bound:path>/three with at least this JSON data is received by mock HTTP server:
      """
      {
        "key1": "value1",
        "key2": <bind:key2Value>
      }
      """

    And dynamic key 'key2Value' should have value 'value2'

  Scenario: Partial match and complete mismatch with dynamic bindings

    Given the following HTTP request is received by mock server:
      | method | path  |
      | POST   | /test |

    And the following mock HTTP response is returned:
      """
      common-response
      """

    And the following HTTP request is received by mock server:
      | method | path  |
      | POST   | /test |

    And mock HTTP request body is a JSON with at least the following data:
      """
      {
        "key1": <bind:key1>,
        "key2": 2
      }
      """

    And the following mock HTTP response is returned:
      """
      specific-response
      """

    When HTTP POST request to /test is made with JSON body:
      """
      {
        "key1": "id1",
        "key2": 3
      }
      """

    Then last HTTP POST request returns the following:
      """
      common-response
      """

    And dynamic key 'key1' is not set'

  Scenario: Request JSON array match

    Given the following HTTP request is received by mock server:
      | method | path  |
      | POST   | /test |

    And the following mock HTTP response is returned:
      """
      common-response
      """

    And the following HTTP request is received by mock server:
      | method | path  |
      | POST   | /test |

    And mock HTTP request body is a JSON with at least the following data:
      """
      [
        {
          "id": <bind:id1>,
          "param": 1
        },
        {
          "id": <bind:id2>,
          "param": 2
        }
      ]
      """

    And the following mock HTTP response is returned:
      """
      specific-response
      """

    When HTTP POST request to /test is made with JSON body:
      """
      [
        {
          "id": "id1",
          "param": 1
        },
        {
          "id": "id2",
          "param": 2
        }
      ]
      """

    Then last HTTP POST request returns the following:
      """
      specific-response
      """

    And HTTP POST request to /test is made with JSON body:
      """
      [
        {
          "id": "id2",
          "param": 2
        }
      ]
      """

    And last HTTP POST request returns the following:
      """
      common-response
      """

  Scenario: Negative HTTP call verification

    When HTTP POST request to /test is made with JSON body:
    """
    {}
    """

    Then no HTTP GET call to /test is made