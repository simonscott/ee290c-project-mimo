package Work

import Chisel._
import Node._
import scala.collection.mutable.HashMap
import scala.math._
import LMSConstants._
import ComplexMathFunctions._
import FixedPoint._

// provides a class to test together the channel estimator and the matrix engine

class ChannelEstimatorEngineIO(implicit params: LMSParams) extends Bundle()
{
	// input flag; will start processing when start goes high
	val start = Bool().asInput

	// resets estimator into initial state to receive new matrix
	val rst = Bool().asInput

	// output flag; goes high when done processing
	val done = Bool().asOutput

	// sequence of training symbols
	val trainSequence = Vec.fill(params.max_train_len){new ComplexSFix(w=params.samp_wd, e=params.samp_exp).asInput}

	// address to training symbol memory
	val trainAddress = UInt(width=2).asOutput

	// stream of input data
	val dataIn = Decoupled( Vec.fill(params.max_ntx_nrx){new ComplexSFix(w=params.samp_wd, e=params.samp_exp).asInput} ).flip()

	// output with channel estimate matrix
	val channelOut = Vec.fill(params.max_ntx_nrx){
                    Vec.fill(params.max_ntx_nrx){new ComplexSFix(w=params.fix_pt_wd, e=params.fix_pt_exp).asOutput}}
	
	// Number of Tx/Rx antennas
	val Nant = UInt(width = REG_WD).asInput
}


class ChannelEstimatorEngine(implicit params: LMSParams) extends Module
{
    val io = new ChannelEstimatorEngineIO()

    val estimator = Module(new ChannelEstimator())
    val engine = Module(new MatrixEngine())

    estimator.io.trainSequence := io.trainSequence
    estimator.io.dataIn <> io.dataIn
    estimator.io.start := io.start
    estimator.io.rst := io.rst
    estimator.io.Nant := io.Nant
    io.channelOut := estimator.io.channelOut
    io.done := estimator.io.done
    io.trainAddress := estimator.io.trainAddress

    engine.io <> estimator.io.toMatEngine  

}

// Tester for testing the matrix engine
class ChannelEstimatorEngineTests(c: ChannelEstimatorEngine, params: LMSParams) extends Tester(c)
{
    val dataIn_r = Array( Array(1,2,1,2), Array(2,1,2,1), Array(1,3,2,2), Array(1,1,1,2) )
    val dataIn_i = Array( Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0) )
    val trainSeq_r = Array( Array(1,1,0,0), Array(0,1,0,0), Array(0,0,1,0), Array(0,0,0,1) )
    val trainSeq_i = Array( Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0) )
    val channelOut_r = Array( Array(3,3,3,3), Array(2,1,3,1), Array(1,2,2,1), Array(2,1,2,2) )
    val channelOut_i = Array( Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0) )
/*
    val dataIn_r = Array( Array(0.9206, -0.1282, 0.3462, 0.0742), Array(2.0,1.0,2.0,1.0), Array(1.0,3.0,2.0,2.0), Array(1.0,1.0,1.0,2.0) )
    val dataIn_i = Array( Array(-0.1505, -1.4132, -0.3132, -1.3199), Array(0.0,0.0,0.0,0.0), Array(0.0,0.0,0.0,0.0), Array(0.0,0.0,0.0,0.0) )
    val trainSeq_r = Array( Array(1,1,1,1), Array(1,1,-1,-1), Array(1,-1,1,-1), Array(1,-1,-1,1) )
    val trainSeq_i = Array( Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0) )
    val channelOut_r = Array( Array(3,3,3,3), Array(2,1,3,1), Array(1,2,2,1), Array(2,1,2,2) )
    val channelOut_i = Array( Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0), Array(0,0,0,0) )
*/
    for (t <- 0 until 1)
    {
        // Apply inputs

	poke(c.io.start, 1)
	poke(c.io.Nant, 4)

        for(i <- 0 until params.max_ntx_nrx) {
		poke(c.io.dataIn.valid, 1)
            for(j <- 0 until params.max_ntx_nrx) {
                poke(c.io.dataIn.bits(j).real.raw, conv_double_to_fp((dataIn_r(i)(j)).toDouble, params.samp_frac_bits, params.samp_wd))
                poke(c.io.dataIn.bits(j).imag.raw, conv_double_to_fp((dataIn_i(i)(j)).toDouble, params.samp_frac_bits, params.samp_wd))
            	poke(c.io.trainSequence(j).real.raw, conv_double_to_fp(trainSeq_r(i)(j), params.samp_frac_bits, params.samp_wd))
            	poke(c.io.trainSequence(j).imag.raw, conv_double_to_fp(trainSeq_i(i)(j), params.samp_frac_bits, params.samp_wd))
            }
	step(1)
	peek(c.io.trainAddress)
	peek(c.io.done)
        }

	step(6)
//	peek(c.io.trainAddress)
//	for (i <- 0 until params.max_ntx_nrx) {
//		for (j<- 0 until params.max_ntx_nrx) {
//			peek(c.estimator.io.toMatEngine.matrixIn(i)(j).real.raw)
//			peek(c.estimator.io.toMatEngine.matrixIn(i)(j).imag.raw)
//		}
//		peek(c.estimator.io.toMatEngine.vectorIn(i).real.raw)
//		peek(c.estimator.io.toMatEngine.vectorIn(i).imag.raw)
//	}
        // Clock the module
//        step(4)

        // Check the output
//        for(i <- 0 until params.max_ntx_nrx) {
//		for(j <- 0 until params.max_ntx_nrx) {
//            		expect(c.io.probe(i)(j).real.raw, conv_double_to_fp(channelOut_r(i)(j), params.fix_pt_frac_bits, params.fix_pt_wd))
//            		expect(c.io.probe(i)(j).imag.raw, conv_double_to_fp(channelOut_i(i)(j), params.fix_pt_frac_bits, params.fix_pt_wd))
//		}
//        }

	peek(c.io.done)
//	for(i <- 0 until params.max_ntx_nrx) {
//          for(j <- 0 until params.max_ntx_nrx) {
//              peek(c.io.channelOut(i)(j).real.raw)
//              peek(c.io.channelOut(i)(j).imag.raw)
//          }
//	}

	poke(c.io.start, 0)
	peek(c.io.done)
	peek(c.io.dataIn.ready)
	step(1)
	poke(c.io.rst, 1)
	peek(c.io.done)
	peek(c.io.dataIn.ready)
	step(1)
	poke(c.io.start, 1)
	peek(c.io.done)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.trainAddress)
	peek(c.io.dataIn.ready)
	step(1)
	poke(c.io.rst, 0)
	peek(c.io.trainAddress)
	peek(c.io.dataIn.ready)

//	for(i <- 0 until params.max_ntx_nrx) {
//		poke(c.io.dataIn.valid, 1)
//		for(j <- 0 until params.max_ntx_nrx) {
//			poke(c.io.dataIn.bits(j).real.raw, conv_double_to_fp(dataIn_r(i)(j)+1, params.samp_exp, params.samp_wd))
//			poke(c.io.dataIn.bits(j).imag.raw, conv_double_to_fp(dataIn_i(i)(j), params.samp_exp, params.samp_wd))
//			poke(c.io.trainSequence(j).real.raw, conv_double_to_fp(trainSeq_r(i)(j), params.samp_exp, params.samp_wd))
//			poke(c.io.trainSequence(j).imag.raw, conv_double_to_fp(trainSeq_i(i)(j), params.samp_exp, params.samp_wd))
//		}
//	step(1)
//	peek(c.io.trainAddress)
//	peek(c.io.done)
//	}

	step(6)
	peek(c.io.done)
	for(i <- 0 until params.max_ntx_nrx) {
		for(j <- 0 until params.max_ntx_nrx) {
			println(conv_fp_to_double(peek(c.io.channelOut(i)(j).real.raw), params.fix_pt_frac_bits, params.fix_pt_wd))
			println(conv_fp_to_double(peek(c.io.channelOut(i)(j).imag.raw), params.fix_pt_frac_bits, params.fix_pt_wd))
		}
	}
/*******
	step(1)
	peek(c.io.trainAddress)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.trainAddress)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.trainAddress)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.trainAddress)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.trainAddress)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.done)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.done)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.done)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.done)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.done)
	peek(c.io.dataIn.ready)
	step(1)
	peek(c.io.done)
	peek(c.io.dataIn.ready)
*********/
	
    }
}

