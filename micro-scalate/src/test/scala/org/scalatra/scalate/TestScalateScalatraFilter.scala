package org.scalatra.scalate

import skinny.micro.SkinnyMicroFilter
import skinny.micro.scalate.ScalateSupport

// The "test" is that this compiles, to avoid repeats of defects like Issue #9.
class TestScalateScalatraFilter extends SkinnyMicroFilter with ScalateSupport
