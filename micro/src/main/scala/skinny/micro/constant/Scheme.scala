package skinny.micro.constant

/**
 * Scheme.
 */
sealed trait Scheme

/**
 * HTTP
 */
case object Http extends Scheme

/**
 * HTTPS
 */
case object Https extends Scheme

