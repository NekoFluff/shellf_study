package com.crazyfluff.shellfstudy.fakes

import mockwebserver3.MockResponse

/**
 * The response every WaniKani collection endpoint returns when a test does not care about that
 * resource: an empty collection with the right `object` and `url`.
 *
 * This was copy-pasted into five test classes (twice under the name `emptyCollectionResponse`), which
 * is how the `code` parameter came to exist in only two of them — the others could not express "and
 * this one fails". One definition, with the status configurable.
 */
fun emptyCollection(objectType: String, code: Int = 200): MockResponse =
    jsonResponse("""{"object":"$objectType","url":"https://api.wanikani.com/v2/$objectType","data":[]}""", code)

/**
 * The same thing as a body, for tests that drive a fake engine or a stubbed dispatcher with raw JSON
 * rather than a [MockResponse]. Was three byte-identical private copies, one per ViewModel test.
 *
 * The `url` is deliberately a placeholder: no code under test reads it, and pretending it names a real
 * endpoint would only invite a test to depend on it.
 */
fun emptyCollectionJson(): String =
    """{"object": "collection", "url": "https://api.wanikani.com/v2/x", "total_count": 0, "data": []}"""
