package scalapplcodefest

import cc.factorie.optimize.{Perceptron, OnlineTrainer, Example}
import cc.factorie.util.DoubleAccumulator
import cc.factorie.la.WeightsMapAccumulator
import cc.factorie.WeightsSet

/**
 * @author Sebastian Riedel
 */
object Trainer {

  def train(model: LinearModel, instances: Seq[State]): Vector = {
    import TermImplicits._
    val weightsSet = new WeightsSet
    val key = weightsSet.newWeights( new DenseVector(10000))
    case class PerceptronExample(instance:State) extends Example {

      val target = instance.target
      val conditioned = model | instance
      val aligned = MessagePassingGraphBuilder.build(conditioned,model.weights)

      def accumulateValueAndGradient(value: DoubleAccumulator, gradient: WeightsMapAccumulator) = {

        aligned.graph.weights = weightsSet(key).asInstanceOf[DenseVector]
        MaxProduct.run(aligned.graph, 1)

        val guessFeats = new SparseVector(100)
        val guessScore = MaxProduct.featureExpectationsAndObjective(aligned.graph, guessFeats)
        val goldScore = conditioned.eval(target).right.get
        value.accumulate(guessScore - goldScore)
        gradient.accumulate(key,guessFeats)

      }
    }
    val examples = instances.map(PerceptronExample)
    val trainer = new OnlineTrainer(weightsSet,new Perceptron)
    trainer.trainFromExamples(examples)

    weightsSet(key).asInstanceOf[Vector]

  }


}