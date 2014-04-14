package Work

import Chisel._
import Node._
import FixedPoint._
import LMSConstants._
import scala.math._


// High-level compilation parameters
// TODO: this must be parameterized based on max packet len, etc
case class LMSParams()
{
    // Simulation parameters
    val max_ntx = 4
    val max_nrx = 4
    val max_train_len = 8
    val fifo_len = 16
    val num_registers = 6

    // Bit widths
    val addr_wd = log2Up( max(num_registers, 6) )
    val samp_wd = 10
    val symbol_wd = log2Up(64)
}


// The complex type (signed integer)
class ComplexSInt(w: Int) extends Bundle() {
    val real = SInt(width = w)
    val imag = SInt(width = w)

    override def clone: this.type = { new ComplexSInt(w).asInstanceOf[this.type]; }
}

// The complex type (signed fixed-point integer)
class ComplexSFix(w: Int) extends Bundle() {
    val real = SFix(exp = 0, width = w)
    val imag = SFix(exp = 0, width = w)

    override def clone: this.type = { new ComplexSFix(w).asInstanceOf[this.type]; }
}

// Constants
object LMSConstants
{
    // Modulation options
    val MOD_BPSK = UInt(0)
    val MOD_QPSK = UInt(1)
    val MOD_16QAM = UInt(2)
    val MOD_64QAM = UInt(3)

    // Fixed bit-widths (i.e. not parameterizable)
    val REG_WD = 4
}


// Configuration registers
// Constants
object ConfigRegisters
{
    val ntx = Reg(init = UInt(0, width = REG_WD))
    val nrx = Reg(init = UInt(0, width = REG_WD))
    val train_len = Reg(init = UInt(0, width = REG_WD))
    val modulation = Reg(init = UInt(0, width = REG_WD))
    val snr = Reg(init = UInt(0, width = REG_WD))
    val start = Reg(init = Bool(false))
}
