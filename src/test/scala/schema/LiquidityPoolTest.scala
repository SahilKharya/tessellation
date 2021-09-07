package schema

import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.Semaphore
import org.scalatest.GivenWhenThen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.tessellation.schema.{DAG, ETH, LiquidityPool}

class LiquidityPoolTest extends AnyFreeSpec with GivenWhenThen with Matchers {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  Given("LiquidityPool with 1 ETH and 100 DAG")
  val ethQ = 1
  val dagQ = 100
  val s = Semaphore[IO](1).unsafeRunSync()
  val lp = new LiquidityPool[ETH, DAG](ethQ, dagQ, "", "")(s)

  When("trader exchanges 1 ETH for DAG")
  val dag = lp.howManyYForX(1, ethQ, dagQ)

  Then("trader gets 50 DAG")
  dag shouldBe 50
}
