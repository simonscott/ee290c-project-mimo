package Work

import Chisel._
import Node._
import scala.collection.mutable.HashMap
import scala.math._
import LMSConstants._
import ComplexMathFunctions._
import FixedPoint._

/*
 * Important TODOs:
 * 1.) Make more thorough test-bench: specifically, need to test with noise
 * 2.) MatrixEngine has 1 cycle latency... need to pipeline!
 * 3.) Adapt the matrix
 */

// I/O interface for the adaptive decoder
class AdaptiveDecoderIO(implicit params: LMSParams) extends Bundle()
{
    val wSeed = Vec.fill(params.max_ntx_nrx){
                    Vec.fill(params.max_ntx_nrx){new ComplexSFix(w=params.fix_pt_wd, e=params.fix_pt_exp).asInput}}

    val samples = Decoupled( Vec.fill(params.max_ntx_nrx){new ComplexSInt(w = params.samp_wd)} ).flip()

    // This contains integers representing the symbols, e.g. 0 for (0 + 0j), 1 for (0 + 1j), 2 for (1 + 0j) 
    val decodedData = Decoupled( Vec.fill(params.max_ntx_nrx){UInt(width = params.symbol_wd)} )

    val toMatEngine = new MatrixEngineIO().flip()

    // When this goes high, a new W matrix will be loaded into the decoder
    val resetW = Bool().asInput
    
    // The AdaptiveDecoder will only load samples from RX Data Queue when this is high (and samples are available).
    // Keep this low when W is being recomputed (begin of new frame).
    val processSamples = Bool().asInput
}


// Module to estimate the channel matrix H
class AdaptiveDecoder(implicit params: LMSParams) extends Module
{
    // Create the I/O
    val io = new AdaptiveDecoderIO()

    // Convert samples to fixed point
    // Samples are ints: treat top 4 bits as integer, rest as fractional.
    val samples_fp = Vec.fill(params.max_ntx_nrx){new ComplexSFix(w=params.fix_pt_wd, e=params.fix_pt_exp)}
    for(i <- 0 until params.max_ntx_nrx)
    {
        samples_fp(i).real.raw := io.samples.bits(i).real << UInt(params.fix_pt_frac_bits - params.samp_wd + params.samp_int_bits)
        samples_fp(i).imag.raw := io.samples.bits(i).imag << UInt(params.fix_pt_frac_bits - params.samp_wd + params.samp_int_bits)
    }


    // ****** Hardware to perform the reading/writing of matrix W ******

    // Create wires
    val processSamples = (io.processSamples & io.samples.valid & (~io.resetW) & io.decodedData.ready)
    val nextW = Vec.fill(params.max_ntx_nrx){ Vec.fill(params.max_ntx_nrx){
                new ComplexSFix(w=params.fix_pt_wd, e=params.fix_pt_exp) } }

    // Create register array to store W
    val w = Vec.fill(params.max_ntx_nrx){ Vec.fill(params.max_ntx_nrx){
                Reg(new ComplexSFix(w=params.fix_pt_wd, e=params.fix_pt_exp)) } }

    for(i <- 0 until params.max_ntx_nrx)
    {
        for(j <- 0 until params.max_ntx_nrx)
        {
            // Initialize register array with W seed
            when(io.resetW) {
                w(i)(j) := io.wSeed(i)(j)
            }

            // Otherwise update W with computed updates
            .elsewhen(processSamples) {
                w(i)(j) := nextW(i)(j)
            }
        }
    }


    // ****** Hardware to compute the transmitted signal ******

    // Compute Wx
    io.toMatEngine.matrixIn := w
    io.toMatEngine.vectorIn := samples_fp
    val Wx = io.toMatEngine.result

    // Use slicer to decode
    // Assume QAM modulation for now
    val symbols = Vec.fill(params.max_ntx_nrx){UInt(width = params.symbol_wd)}
    for(i <- 0 until params.max_ntx_nrx)
    {
        when(Wx(i).imag.raw >= SInt(0) && Wx(i).real.raw >= SInt(0)) {
            symbols(i) := UInt(0)
        }
        .elsewhen(Wx(i).imag.raw >= SInt(0) && Wx(i).real.raw < SInt(0)) {
            symbols(i) := UInt(1)
        }
        .elsewhen(Wx(i).imag.raw < SInt(0) && Wx(i).real.raw >= SInt(0)) {
            symbols(i) := UInt(3)
        }
        .otherwise {
            symbols(i) := UInt(2)
        }

        io.decodedData.bits(i) := symbols(i)
    }

    io.decodedData.valid := processSamples


    // ****** Hardware to adapt the W matrix ******

    // Lots of TODO
    // Compute error, using a lookup table to compare computed symbol to predicted symbol
}


// I/O interface: FOR TESTING ONLY
class AdaptiveDecoderWithMatricEngIO(implicit params: LMSParams) extends Bundle()
{
    val wSeed = Vec.fill(params.max_ntx_nrx){
                    Vec.fill(params.max_ntx_nrx){new ComplexSFix(w=params.fix_pt_wd, e=params.fix_pt_exp).asInput}}

    val samples = Decoupled( Vec.fill(params.max_ntx_nrx){new ComplexSInt(w = params.samp_wd)} ).flip()

    val decodedData = Decoupled( Vec.fill(params.max_ntx_nrx){UInt(width = params.symbol_wd)} )

    val resetW = Bool().asInput
    
    val processSamples = Bool().asInput
}

// Module: FOR TESTING ONLY
class AdaptiveDecoderWithMatrixEng(implicit params: LMSParams) extends Module
{
    val io = new AdaptiveDecoderWithMatricEngIO()

    val adaptiveDecoder = Module(new AdaptiveDecoder)
    val matrixEngine = Module(new MatrixEngine)

    adaptiveDecoder.io.toMatEngine      <> matrixEngine.io
    adaptiveDecoder.io.wSeed            <> io.wSeed
    adaptiveDecoder.io.samples          <> io.samples
    adaptiveDecoder.io.decodedData      <> io.decodedData
    adaptiveDecoder.io.resetW           := io.resetW
    adaptiveDecoder.io.processSamples   := io.processSamples
}


// Tester for testing the adaptive decoder
class AdaptiveDecoderTests(c: AdaptiveDecoderWithMatrixEng, params: LMSParams) extends Tester(c)
{
    // Function to convert double numbers to fixed point
    def conv_double_to_fp(d: Double, fp_frac_bits: Int, fp_bit_wd: Int): Int = 
    {
        var fp = (d * pow(2, fp_frac_bits)).toInt
        fp = if(fp < 0) ( pow(2, fp_bit_wd).toInt + fp ) else fp
        return fp
    }

    // Function to convert fixed point numbers to doubles
    def conv_fp_to_double(fp: BigInt, fp_frac_bits: Int, fp_bit_wd: Int): Double = 
    {
        var d = if(fp > pow(2, fp_bit_wd-1).toInt) (fp.toDouble - pow(2, fp_bit_wd)) else fp.toDouble
        d = d * pow(2, -fp_frac_bits)
        return d
    }

    // Function to convert double numbers to integer samples
    def conv_double_to_samp(d: Double, samp_int_bits: Int, samp_bit_wd: Int): Int = 
    {
        var samp = (d * pow(2, samp_bit_wd - samp_int_bits)).toInt
        samp = if(samp < 0) ( pow(2, samp_bit_wd).toInt + samp ) else samp
        return samp
    }

    // Test vectors
    // Transmitted sequence is: [1 + 1j, -1 + 1j, -1 -1j, +1 - 1j]
    val test_wseed_in_r = Array( Array(-0.672, 0.278, 0.642, -0.318), Array(0.367, -0.402, -0.333, 0.302), Array(1.014, 0.040, -1.128, 0.429), Array(-0.419, 0.230, 0.980, -0.425) )
    val test_wseed_in_i = Array( Array(1.095, -0.694, -0.888, -0.356), Array(-0.149, 0.664, -0.103, -0.307), Array(-0.999, 0.410, 1.021, 0.363), Array(-0.222, -0.290, -0.123, 0.063) )
    val test_samples_in_r = Array(0.614, 0.429, 0.419, -2.704)
    val test_samples_in_i = Array(-1.015, -0.215, -0.905, 0.018)
    val test_symbols_out = Array(0, 1, 2, 3)
    
    // Load the W matrix
    poke(c.io.resetW, 1)
    poke(c.io.processSamples, 0)

    for(i <- 0 until params.max_ntx_nrx) {
        for(j <- 0 until params.max_ntx_nrx) {
            poke( c.io.wSeed(i)(j).real.raw, conv_double_to_fp(test_wseed_in_r(i)(j), params.fix_pt_frac_bits, params.fix_pt_wd) )
            poke( c.io.wSeed(i)(j).imag.raw, conv_double_to_fp(test_wseed_in_i(i)(j), params.fix_pt_frac_bits, params.fix_pt_wd) )
        }
    }

    // Clock the module
    step(1)

    // Load the samples
    poke(c.io.resetW, 0)
    poke(c.io.processSamples, 1)
    poke(c.io.samples.valid, 1)
    poke(c.io.decodedData.ready, 1)
    for(i <- 0 until params.max_ntx_nrx) {
        poke( c.io.samples.bits(i).real, conv_double_to_samp(test_samples_in_r(i), params.samp_int_bits, params.samp_wd) )
        poke( c.io.samples.bits(i).imag, conv_double_to_samp(test_samples_in_i(i), params.samp_int_bits, params.samp_wd) )
    }

    // Clock the module
    step(2)

    // Peek at various stages of the computation
    expect(c.io.decodedData.valid, 1)
    peek(c.adaptiveDecoder.io.toMatEngine.vectorIn)
    val y = peek(c.adaptiveDecoder.io.toMatEngine.result)

    for(yi <- y) {
        print( conv_fp_to_double(yi, params.fix_pt_frac_bits, params.fix_pt_wd) )
        print(", ")
    }
    println()
    
    // Check that the symbols were decoded correctly
    for(i <- 0 until params.max_ntx_nrx) {
        expect( c.io.decodedData.bits(i), test_symbols_out(i) )
    }
}


