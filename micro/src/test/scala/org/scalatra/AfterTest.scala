package org.scalatra

import org.scalatra.test.scalatest.ScalatraFunSuite
import skinny.micro.base.BeforeAfterDsl
import skinny.micro.routing.RoutingDsl
import skinny.micro.{ Action, SkinnyMicroServlet, SkinnyMicroBase }

class AfterTestServlet extends SkinnyMicroServlet with AfterTestAppBase

trait AfterTestAppBase
    extends SkinnyMicroBase
    with RoutingDsl
    with BeforeAfterDsl {

  after() {
    response.setStatus(204)
  }

  after("/some/path") {
    response.setStatus(202)
  }

  after("/other/path") {
    response.setStatus(206)
  }

  get("/some/path") {}

  get("/other/path") {}

  get("/third/path") {}

}

class AfterServletTest extends AfterTest {
  mount(classOf[AfterTestServlet], "/*")
}

abstract class AfterTest extends ScalatraFunSuite {

  test("afterAll is applied to all paths") {
    get("/third/path") {
      status should equal(204)
    }
  }

  test("after only applies to a given path") {
    get("/some/path") {
      status should equal(202)
    }
    get("/other/path") {
      status should equal(206)
    }
  }

}
