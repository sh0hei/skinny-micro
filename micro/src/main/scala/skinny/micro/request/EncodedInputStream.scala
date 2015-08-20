package skinny.micro.request

import java.io.InputStream
import javax.servlet.ServletInputStream

/**
 * Encoded input stream in a Servlet request.
 */
private[skinny] class EncodedInputStream(
    encoded: InputStream,
    raw: ServletInputStream) extends ServletInputStream {

  override def read(): Int = encoded.read()
  override def read(b: Array[Byte]): Int = read(b, 0, b.length)
  override def read(b: Array[Byte], off: Int, len: Int) = encoded.read(b, off, len)

}
