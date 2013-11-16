package scalapplcodefest

import scalaxy.loops._
import java.util
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable
import cc.factorie.maths
import cc.factorie.maths.ArrayOps


/**
 * @author Sebastian Riedel
 */
object MaxProduct {


  def main(args: Array[String]) {
    val fg = new FG
    val f1 = fg.createFactor2(Array(Array(1.0, 2.0, 3.0), Array(4.0, 5.0, 6.0)))
    val n1 = fg.createNode(2)
    val n2 = fg.createNode(3)
    val e1 = fg.createEdge(f1, n1)
    val e2 = fg.createEdge(f1, n2)
    fg.build()

    MaxProduct.runMessagePassing(fg, 1)

    println(n1.b.mkString(" "))
    println(n2.b.mkString(" "))

  }

  def runMessagePassing(fg: FG, maxIteration: Int) {
    for (i <- (0 until maxIteration).optimized) {
      for (edge <- fg.edges) {
        updateN2F(edge)
        updateF2N(edge)
      }
    }
    for (node <- fg.nodes) updateBelief(node)
  }

  def updateF2N(edge: FG#Edge) {
    val factor = edge.f
    util.Arrays.fill(edge.f2n, Double.MinValue)
    for (i <- 0 until factor.entryCount) {
      val setting = factor.settings(i)
      var score = factor.score(i)
      val varValue = setting(edge.indexInFactor)
      for (j <- 0 until factor.rank; if j != edge.indexInFactor) {
        score += factor.edges(j).n2f(setting(j))
      }
      edge.f2n(varValue) = math.max(score, edge.f2n(varValue))
    }
  }

  def updateN2F(edge: FG#Edge) {
    val node = edge.n
    System.arraycopy(node.in, 0, edge.n2f, 0, edge.n2f.length)
    for (i <- 0 until node.dim) {
      for (e <- 0 until node.edges.length; if e != edge.indexInNode)
        edge.n2f(i) += node.edges(e).f2n(i)
    }
  }

  def updateBelief(node: FG#Node) {
    System.arraycopy(node.in, 0, node.b, 0, node.b.length)
    for (e <- 0 until node.edges.length)
      for (i <- 0 until node.dim)
        node.b(i) += node.edges(e).f2n(i)
  }
}

class FG {

  val edges = new ArrayBuffer[Edge]
  val nodes = new ArrayBuffer[Node]
  val factors = new ArrayBuffer[Factor]

  final class Node(val index:Int, val dim: Int) {
    var edges: Array[Edge] = null
    private[FG] var edgeCount: Int = 0
    private[FG] var edgeFilled: Int = 0
    val b = Array.ofDim[Double](dim)
    val in = Array.ofDim[Double](dim)

    override def toString = {
      """-----------------
        |Node:   %d
        |Belief:
        |%s
      """.stripMargin.format(index,b.mkString("\n"))
    }


  }

  final class Edge(val n: Node, val f: Factor, val dim: Int) {
    val n2f = Array.ofDim[Double](dim)
    val f2n = Array.ofDim[Double](dim)
    var indexInFactor = -1
    var indexInNode = -1
  }

  final class Factor(val index:Int, val table: Array[Double], val dims: Array[Int], val settings: Array[Array[Int]]) {
    var edges: Array[Edge] = null
    private[FG] var edgeCount: Int = 0
    private[FG] var edgeFilled: Int = 0
    def rank = dims.length
    val entryCount = {
      var result = 1
      for (i <- (0 until dims.length).optimized) result *= dims(i)
      result
    }
    def score(entry: Int): Double = {
      table(entry)
    }
    override def toString = {
      val tableString = for ((setting,index) <- settings.zipWithIndex) yield
        s"${setting.mkString(" ")} ${table(index)}"
      """-----------------
        |Factor:  %d
        |Nodes:   %s
        |Table:
        |%s
      """.stripMargin.format(index, edges.map(_.n.index).mkString(" "),tableString.mkString("\n"))
    }
  }

  def createNode(dim: Int) = {
    val n = new Node(nodes.size, dim)
    nodes += n
    n
  }

  def createEdge(f: Factor, n: Node) = {
    val e = new Edge(n, f, n.dim)
    n.edgeCount += 1
    f.edgeCount += 1
    edges += e
    e
  }

  def build() {
    for (edge <- edges) {
      if (edge.f.edges == null) edge.f.edges = Array.ofDim[Edge](edge.f.edgeCount)
      if (edge.n.edges == null) edge.n.edges = Array.ofDim[Edge](edge.n.edgeCount)
      edge.indexInFactor = edge.f.edgeFilled
      edge.indexInNode = edge.n.edgeFilled
      edge.f.edges(edge.indexInFactor) = edge
      edge.n.edges(edge.indexInNode) = edge
      edge.f.edgeFilled += 1
      edge.n.edgeFilled += 1
    }
  }


  override def toString = {
    """
      |Nodes:
      |%s
      |
      |Factors:
      |%s
    """.stripMargin.format(nodes.mkString("\n"),factors.mkString("\n"))
  }
  def createFactor1(table: Array[Double]) = {
    val f = new Factor(factors.size, table, Array(table.length), Range(0, table.length).map(Array(_)).toArray)
    factors += f
    f
  }

  def createFactor(scores: Array[Double], settings: Array[Array[Int]], dims: Array[Int]) = {
    val f = new Factor(factors.size, scores, dims, settings)
    factors += f
    f
  }

  def createFactor2(table: Array[Array[Double]]) = {
    val dims = Array(table.length, table(0).length)
    val entryCount = dims(0) * dims(1)
    val scores = Array.ofDim[Double](entryCount)
    val settings = Array.ofDim[Int](entryCount, dims.length)

    for (i1 <- 0 until dims(0);
         i2 <- 0 until dims(1)) {
      val entry = i1 + i2 * dims(0)
      scores(entry) = table(i1)(i2)
      settings(entry) = Array(i1, i2)
    }
    val f = new Factor(factors.size, scores, dims, settings)
    factors += f
    f
  }

  def createFactor3(table: Array[Array[Array[Double]]]) = {
    val dims = Array(table.length, table(0).length, table(0)(0).length)
    val entryCount = dims(0) * dims(1) * dims(2)
    val scores = Array.ofDim[Double](entryCount)
    val settings = Array.ofDim[Int](entryCount, dims.length)

    for (i1 <- 0 until dims(0);
         i2 <- 0 until dims(1);
         i3 <- 0 until dims(2)) {
      val entry = i1 + i2 * dims(0) + i3 * dims(1) * dims(0)
      scores(entry) = table(i1)(i2)(i3)
      settings(entry) = Array(i1, i2, i3)
    }
    val f = new Factor(factors.size, scores, dims, settings)
    factors += f
    f
  }
}

object FGBuilder {

  class TermAlignedFG(val term:Term[Double]) {
    val vars = term.variables.toSeq
    val fg = new FG
    def createVariableMapping(variable:Variable[Any]) = {
      val domain = variable.domain.eval().get.toSeq
      val indexOfValue = domain.zipWithIndex.toMap
      val node = fg.createNode(domain.size)
      VariableMapping(variable, node, domain, indexOfValue)
    }
    val variableMappings = vars.map(createVariableMapping)
    val dims = variableMappings.map(_.dom.size)
    val entryCount = dims.product

    def beliefToState() = {
      val map = for (v <- variableMappings) yield {
        val winner = ArrayOps.maxIndex(v.node.b)
        val value = v.dom(winner)
        v.variable -> value
      }
      State(map.toMap)
    }
  }

  case class VariableMapping(variable:Variable[Any],node:FG#Node, dom:Seq[Any], indexOfValue:Map[Any,Int])

  def main(args: Array[String]) {
    import TermImplicits._
    val r = 'r of (0 ~~ 1 |-> Bools)
    val s = 's of (0 ~~ 1 |-> Bools)

    val f = dsum(for (i <- 0 ~~ 1) yield I(!(r(i) || s(i))))

    println(f.eval(r.atom(0) -> true, s.atom(0) -> false))

    val fg = build(f)
    println(fg.fg)
    println(fg.fg.nodes.size)
    println(fg.fg.factors.size)
    println(fg.fg.factors.head.rank)

    MaxProduct.runMessagePassing(fg.fg,1)
    println(fg.beliefToState().toPrettyString)



  }

  def build(t: Term[Double]): TermAlignedFG = {
    val aligned = new TermAlignedFG(t)
    import aligned._
    //iterate over possible states, get the state index
    val settings = Array.ofDim[Array[Int]](entryCount)
    val scores = Array.ofDim[Double](entryCount)
    for (state <- State.allStates(vars.toList)) {
      val setting = Array.ofDim[Int](vars.size)
      val score = term.eval(state).get
      var stateIndex = 0
      for ((v,i) <- variableMappings.zipWithIndex) {
        val valueIndex = v.indexOfValue(state(v.variable))
        stateIndex = valueIndex + v.dom.size * stateIndex
        setting(i) = valueIndex
      }
      settings(stateIndex) = setting
      scores(stateIndex) = score
    }
    val f = fg.createFactor(scores, settings, dims.toArray)
    for (n <- fg.nodes) {
      fg.createEdge(f, n)
    }
    fg.build()
    aligned
  }
}

