package example

import org.scalatra.test.scalatest.ScalatraFlatSpec
import skinny.micro.SkinnyMicroServlet
import skinny.micro.json.SkinnyMicroJSONStringOps

class JSONOperationSpec extends ScalatraFlatSpec {

  object App extends SkinnyMicroServlet with SkinnyMicroJSONStringOps {
    def name = params.getAs[String]("name").getOrElse("Anonymous")

    get("/hello") {
      responseAsJSON(Map("message" -> s"Hello, $name"))
    }
  }
  addServlet(App, "/*")

  it should "work" in {
    get("/hello") {
      status should equal(200)
      header("Content-Type") should equal("application/json; charset=utf-8")
      body should equal("""{"message":"Hello, Anonymous"}""")
    }

    get("/hello?name=Martin") {
      status should equal(200)
      header("Content-Type") should equal("application/json; charset=utf-8")
      body should equal("""{"message":"Hello, Martin"}""")
    }
  }

}
