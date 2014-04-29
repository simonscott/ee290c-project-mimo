package Work

import Chisel._
import Node._
import scala.collection.mutable.HashMap
import scala.math._
import LMSConstants._
import ComplexMathFunctions._
import FixedPoint._

class FixDivisionIO(implicit params: LMSParams) extends Bundle()
{
	val num = new ComplexSFix(w=params.fix_pt_wd,e=params.fix_pt_exp).asInput
	val den = new ComplexSFix(w=params.fix_pt_wd,e=params.fix_pt_exp).asInput
	val result = new ComplexSFix(w=params.fix_pt_wd,e=params.fix_pt_exp).asOutput
}

class FixDivision (implicit params:LMSParams) extends Module
{
	val io = new FixDivisionIO()

	val den_mag = SFix(exp = params.fix_pt_exp, width = params.fix_pt_wd)
	den_mag := io.den.real * io.den.real + io.den.imag * io.den.imag

	val product = new ComplexSFix(w=20,e=8)
	product := complex_mult(io.num, conj(io.den))

	io.result.real.raw := (product.real.raw << UInt(params.fix_pt_wd - params.fix_pt_exp)) / den_mag.raw
	io.result.imag.raw := (product.imag.raw << UInt(params.fix_pt_wd - params.fix_pt_exp)) / den_mag.raw
}

class FixDivisionTests(c: FixDivision, params: LMSParams) extends Tester(c)
{
val num_r = conv_double_to_fp(0.9, params.fix_pt_frac_bits, params.fix_pt_wd)
val num_i = conv_double_to_fp(0.2, params.fix_pt_frac_bits, params.fix_pt_wd)
val den_r = conv_double_to_fp(0.6, params.fix_pt_frac_bits, params.fix_pt_wd)
val den_i = conv_double_to_fp(-1.3, params.fix_pt_frac_bits, params.fix_pt_wd)
val result_r = 1
val result_i = 0

for (t <- 0 until 1)
    {
        // Apply inputs
	poke(c.io.num.real.raw, num_r)
        poke(c.io.num.imag.raw, num_i)
        poke(c.io.den.real.raw, den_r)
        poke(c.io.den.imag.raw, den_i)

        // Clock the module
        step(1)

        // Check the output
	val yr = peek(c.io.result.real.raw)
	val yi = peek(c.io.result.imag.raw)

	print( conv_fp_to_double(yr, params.fix_pt_frac_bits, params.fix_pt_wd) )
	println()
	print( conv_fp_to_double(yi, params.fix_pt_frac_bits, params.fix_pt_wd) )
	println()
    }
}
