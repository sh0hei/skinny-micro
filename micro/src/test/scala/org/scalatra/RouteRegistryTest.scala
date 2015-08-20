package org.scalatra

import org.scalatra.test.scalatest.ScalatraFunSuite
import skinny.micro.SkinnyMicroServlet

object RouteRegistryTestServlet extends SkinnyMicroServlet {
  get("/foo") {}
  post("/foo/:bar") {}
  put("""^/foo.../bar$""".r) {}
  get("/nothing", false) {}
  get(false) {}

  def renderRouteRegistry: String = routes.toString
}

class RouteRegistryTest extends ScalatraFunSuite {

  test("route registry string representation contains the entry points") {
    RouteRegistryTestServlet.renderRouteRegistry should equal(List(
      "GET /foo",
      "GET /nothing [Boolean Guard]",
      "GET [Boolean Guard]",
      "POST /foo/:bar",
      "PUT ^/foo.../bar$"
    ) mkString ", ")
  }
}
