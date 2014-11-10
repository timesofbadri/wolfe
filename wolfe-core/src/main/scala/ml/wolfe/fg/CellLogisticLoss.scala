package ml.wolfe.fg

import cc.factorie.la.SparseTensor1
import ml.wolfe.FactorGraph._
import ml.wolfe.FactorieVector

/**
 * @author Sebastian Riedel
 * @author rockt
 */
class CellLogisticLoss(rowEdge: Edge, columnEdge: Edge, target: Double = 1.0, val lambda: Double = 0.0, weight: Double = 1.0) extends Potential with Regularization {
  //nodes of edges may change hence the def and not val.
  def rowVar = rowEdge.n.variable.asVector
  def columnVar = columnEdge.n.variable.asVector
  val rowMsgs    = rowEdge.msgs.asVector
  val columnMsgs = columnEdge.msgs.asVector

  def sig(x: Double) = 1.0 / (1.0 + math.exp(-x))

  private def innerLossAndDirection(s: Double): (Double, Int) =
    if (target >= s) (1 + s - target, 1)
    else (1 + target - s, -1)

  override def valueForCurrentSetting(): Double = {
    val a = rowVar.setting
    val v = columnVar.setting
    val s = sig(a dot v)
    val loss = innerLossAndDirection(s)._1
    math.log(loss) * weight + regLoss(a) + regLoss(v)
  }

  override def valueAndGradientForAllEdges(): Double = {
    val s = sig(rowMsgs.n2f dot columnMsgs.n2f)
    val (loss, dir) = innerLossAndDirection(s)
    rowMsgs.f2n = (columnMsgs.n2f * (1.0 - loss) * dir) * weight + regGradient(rowMsgs.n2f)
    columnMsgs.f2n = (rowMsgs.n2f * (1.0 - loss) * dir) * weight + regGradient(columnMsgs.n2f)
    math.log(loss) * weight + regLoss(rowMsgs.n2f) + regLoss(columnMsgs.n2f)
  }
}

/**
 * @author Sebastian Riedel
 */
class CellLogisticLoss2(rowEdge: Edge, columnEdge: Edge, truth: Double = 1.0) extends Potential  {
  //nodes of edges may change hence the def and not val.
  def rowVar = rowEdge.n.variable.asVector
  def columnVar = columnEdge.n.variable.asVector
  val rowMsgs    = rowEdge.msgs.asVector
  val columnMsgs = columnEdge.msgs.asVector



  override def valueAndGradientForAllEdges(): Double = {
    val s = rowMsgs.n2f dot columnMsgs.n2f
    val Z = 1 + math.exp(s)
    val logZ = math.log(Z)
    val o = truth * s - logZ
    val p = math.exp(s - logZ)
    rowMsgs.f2n = columnMsgs.n2f * (truth - p)
    columnMsgs.f2n = rowMsgs.n2f * (truth - p)
    o
  }
}


trait Regularization {
  def regLoss(vector: FactorieVector): Double = 0
  //all zeros
  def regGradient(vector: FactorieVector): FactorieVector = new SparseTensor1(vector.length)
}

/**
 * λ||x||²
 */
trait L2Regularization extends Regularization {
  val lambda: Double
  override def regLoss(vector: FactorieVector): Double =
    if (lambda == 0) 0
    else -lambda * vector.twoNormSquared
  override def regGradient(vector: FactorieVector): FactorieVector =
    if (lambda == 0) new SparseTensor1(vector.length)
    else vector * lambda * -2
}


/**
 * Bayesian Pairwise Ranking objective as in Rendle, Steffen, et al. "BPR: Bayesian personalized ranking from implicit feedback." Proceedings of the Twenty-Fifth Conference on Uncertainty in Artificial Intelligence. AUAI Press, 2009.
 *
 * @param const1Edge edge to node that represents an entity pair that has been observed in the relation
 * @param const2Edge edge to node that represents an entity pair that has *not* been observed in the relation
 * @param predEdge edge to node that represents the relation
 * @param target target value
 * @param lambda regualarization parameter
 */
class BPRPotential(const1Edge: Edge, const2Edge: Edge, predEdge: Edge, target: Double = 1.0, val lambda: Double = 0.0) extends Potential with Regularization {
  def c1Var  = const1Edge.n.variable.asVector
  def c2Var  = const2Edge.n.variable.asVector
  def pVar   = predEdge.n.variable.asVector
  val c1Msgs = const1Edge.msgs.asVector
  val c2Msgs = const2Edge.msgs.asVector
  val pMsgs  = predEdge.msgs.asVector

  def sig(x: Double) = 1.0 / (1.0 + math.exp(-x))

  private def innerLossAndDirection(s: Double): (Double, Int) =
    if (target >= s) (1 + s - target, 1)
    else (1 + target - s, -1)

  override def valueForCurrentSetting(): Double = {
    val c1 = c1Var.setting
    val c2 = c2Var.setting
    val p = pVar.setting
    val s = sig((p dot c1) - (p dot c2))

    val loss = innerLossAndDirection(s)._1
    math.log(loss) + regLoss(c1) + regLoss(c2) + regLoss(p)
  }

  override def valueAndGradientForAllEdges(): Double = {
    val c1 = c1Msgs.n2f
    val c2 = c2Msgs.n2f
    val p = pMsgs.n2f
    val s = sig((p dot c1) - (p dot c2))

    val (loss, dir) = innerLossAndDirection(s)

    c1Msgs.f2n = p * ((1.0 - loss) * dir) + regGradient(c1)
    c2Msgs.f2n = p * -((1.0 - loss) * dir) + regGradient(c2)
    pMsgs.f2n = (c1 - c2) * ((1.0 - loss) * dir) + regGradient(p)

    math.log(loss) + regLoss(c1) + regLoss(c2) + regLoss(p)
  }
}
