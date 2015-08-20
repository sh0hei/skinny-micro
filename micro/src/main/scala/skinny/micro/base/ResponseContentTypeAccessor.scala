package skinny.micro.base

import skinny.micro.SkinnyMicroBase
import skinny.micro.context.SkinnyMicroContext
import skinny.micro.implicits.ServletApiImplicits

/**
 * Provides DSL to access the context type in response.
 */
trait ResponseContentTypeAccessor extends ServletApiImplicits { self: SkinnyMicroBase =>

  /**
   * Gets the content type of the current response.
   */
  def contentType(implicit ctx: SkinnyMicroContext = context): String = {
    ctx.response.contentType.orNull[String]
  }

  /**
   * Sets the content type of the current response.
   */
  def contentType_=(contentType: String)(implicit ctx: SkinnyMicroContext = context): Unit = {
    ctx.response.contentType = Option(contentType)
  }

}
