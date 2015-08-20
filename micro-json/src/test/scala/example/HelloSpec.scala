package example

import org.scalatra.test.scalatest.ScalatraFlatSpec
import skinny.micro._
import skinny.micro.async.AsyncResult
import skinny.micro.json.SkinnyMicroJSONStringOps

import scala.concurrent.Future

object Hello extends WebApp with SkinnyMicroJSONStringOps {

  def message(implicit ctx: Context) = {
    s"Hello, ${params(ctx).getOrElse("name", "Anonymous")}"
  }

  // synchronous action
  get("/hello")(message)
  post("/hello")(message)

  // asynchronous action
  get("/hello/async") {
    implicit val ctx = context
    Future { message(ctx) }
  }

  // returns JSON response
  get("/hello/json") {
    responseAsJSON(Map("message" -> message))
  }
  get("/hello/json/async") {
    AsyncResult {
      responseAsJSON(Map("message" -> s"Hello, ${params.getOrElse("name", "Anonymous")}"))
    }
  }

  get("/dynamic") {
    Future {
      request
    }
  }
}

class HelloSpec extends ScalatraFlatSpec {
  addFilter(Hello, "/*")

  it should "work fine with GET Requests" in {
    get("/hello") {
      status should equal(200)
      body should equal("Hello, Anonymous")
    }
    get("/hello?name=Martin") {
      status should equal(200)
      body should equal("Hello, Martin")
    }
  }

  it should "work fine with POST Requests" in {
    post("/hello", Map()) {
      status should equal(200)
      body should equal("Hello, Anonymous")
    }
    post("/hello", Map("name" -> "Martin")) {
      status should equal(200)
      body should equal("Hello, Martin")
    }
  }

  it should "work fine with AsyncResult" in {
    get("/hello/async") {
      status should equal(200)
      body should equal("Hello, Anonymous")
    }
    get("/hello/async?name=Martin") {
      status should equal(200)
      body should equal("Hello, Martin")
    }
  }

  it should "return JSON response" in {
    get("/hello/json") {
      status should equal(200)
      header("Content-Type") should equal("application/json; charset=utf-8")
      body should equal("""{"message":"Hello, Anonymous"}""")
    }
    get("/hello/json/async?name=Martin") {
      status should equal(200)
      header("Content-Type") should equal("application/json; charset=utf-8")
      body should equal("""{"message":"Hello, Martin"}""")
    }
  }

  it should "detect dynamic value access when the first access" in {
    get("/dynamic") {
      status should equal(500)
    }
  }
}
