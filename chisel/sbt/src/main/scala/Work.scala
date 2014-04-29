package Work

import Chisel._
  
object Work {

  def main(args: Array[String]):Unit = {

    // Parse parameters: set LMS params
    // TODO: actually parse the parameters
    val params = new LMSParams()

    // Parse parameters: figure out which module to test
    val test_module = """-testmodule_(.*)""".r.findFirstMatchIn(args(1))
    require(test_module.isDefined, "Second argument must be -testmodule_ModuleName")

    // Test the appropriate module
    test_module.get.group(1) match {
        case "ChannelEstimatorEngine" => 
            chiselMainTest( args.slice(2, args.length), () => Module(new ChannelEstimatorEngine()(params)) ) {
                c => new ChannelEstimatorEngineTests(c, params) }

        case "MatrixEngine" =>
            chiselMainTest( args.slice(2, args.length), () => Module(new MatrixEngine()(params)) ) {
                c => new MatrixEngineTests(c, params) }
 
        case "AdaptiveDecoder" =>
            chiselMainTest( args.slice(2, args.length), () => Module(new AdaptiveDecoderWithMatrixEng()(params)) ) {
                c => new AdaptiveDecoderTests(c, params) }

        case "FixDivision" => 
            chiselMainTest( args.slice(2, args.length), () => Module(new FixDivision()(params)) ) {
                c => new FixDivisionTests(c, params) }

        case "FixTest" => 
            chiselMainTest( args.slice(2, args.length), () => Module(new FixTest()(params)) ) {
                c => new FixTestTests(c, params) }

        case "Mat2Inverse" => 
            chiselMainTest( args.slice(2, args.length), () => Module(new Mat2Inverse()(params)) ) {
                c => new Mat2InverseTests(c, params) }

    }
  }
}   
