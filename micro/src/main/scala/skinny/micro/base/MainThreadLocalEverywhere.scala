package skinny.micro.base

import javax.servlet.ServletContext

import skinny.micro.context.SkinnyMicroContext

/**
 * When this trait is activated, thread-local request/response needed by SkinnyMicroContext are always accessible.
 */
trait MainThreadLocalEverywhere
    extends SkinnyMicroContextInitializer {

  self: ServletContextAccessor with UnstableAccessValidationConfig =>

  /**
   * Skinny Micro Context
   */
  override implicit def skinnyMicroContext(implicit ctx: ServletContext): SkinnyMicroContext = {
    super.skinnyMicroContext(ctx)
  }

}
