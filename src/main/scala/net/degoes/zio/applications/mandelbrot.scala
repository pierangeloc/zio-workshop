package net.degoes.zio.applications

import net.degoes.zio.applications.mandelbrot.Canvas.CanvasLive
import scalafx.scene.canvas.{Canvas => SCanvas}
import scalafx.scene.paint.Color
import scalaz.zio.console.Console
import scalaz.zio.clock
import scalaz.zio.clock.Clock
import scalaz.zio.{DefaultRuntime, UIO, ZIO, console}

import scala.annotation.tailrec
/**
 * 
 * zio-workshop - 2019-03-29
 * Created with ♥ in Amsterdam
 */
object mandelbrot {
  /*
  We generate a grid of 600 x 400 complex nrs and map each nr to a colour
  */

  case class ColoredPoint(x: Int, y: Int, color: Color) {
    override def toString: String = s"($x, $y, [${color.red}, ${color.green}, ${color.blue}])"
  }
  case class ColoredBitmap(coloredPoints: List[ColoredPoint])
  //canvas component
  sealed trait Canvas {
    val canvas: Canvas.Service[Any]
  }

  object Canvas {
    trait Service[-R] {
      def drawPoint(p: ColoredPoint): ZIO[R, Nothing, Unit]
    }

    trait CanvasLive extends Canvas {
      val scanvas: SCanvas
      val canvas = new Canvas.Service[Any] {
        override def drawPoint(coloredPoint: ColoredPoint): ZIO[Any, Nothing, Unit] = {
          def setColor: UIO[Unit] = ZIO.effect(scanvas.graphicsContext2D.setFill(coloredPoint.color)).catchAll { e => {
              e.printStackTrace()
              UIO.succeed(())
            }
          }

          def drawOval: UIO[Unit] = ZIO.effectTotal(scanvas.graphicsContext2D.fillOval(coloredPoint.x, coloredPoint.y, 1, 1))

            //issue: how to share context of this scanvas if points are drawn in parallel ? maybe with a semaphore with 1 permit
          setColor *> drawOval
        }
      }
    }

    object CanvasLive {
      def withScanvas(c: SCanvas): CanvasLive = new CanvasLive {
        override val scanvas: SCanvas = c
      }
    }
  }

  object canvasCapability extends Canvas.Service[Canvas] {
    override def drawPoint(p: ColoredPoint): ZIO[Canvas, Nothing, Unit] = ZIO.accessM(_.canvas.drawPoint(p))
  }

  case class Frame(width: Int = 600, height: Int = 400) {
    def allPoints: List[(Int, Int)] = for {
      xx <- (0 until width).toList
      yy <- (0 until height).toList
    } yield (xx, yy)
  }

  case class ComplexRectangle(
    xMin: Double = -2.0,
    xMax: Double = 1.0,
    yMin: Double = -1.0,
    yMax: Double = 1.0
  ) {

    def pixelToComplex(frame: Frame)(x: Int, y: Int): Complex =
      Complex(
        xMin + x * (xMax - xMin) / frame.width,
        yMin + y * (yMax - yMin) / frame.height
      )
  }

  case class Complex(x: Double, y: Double) { self =>
    def +(other: Complex): Complex = Complex(self.x + other.x, self.y + other.y)
    def *(other: Complex): Complex = (self, other) match {
      case (Complex(a, b), Complex(c, d)) => Complex(a * c - b * d, a * d + b * c)
    }
    def abs: Double = math.sqrt(x * x + y * y)
  }

  object Complex {
    val zero = Complex(0, 0)
    val one = Complex(1, 0)
  }

  class MandelbrotAlgo(maxIterations:Int) {

    def iterate(c: Complex, bailout: Int): Int = {

      @tailrec
      def run(z: Complex, iter: Int): Int =
        if (iter >= maxIterations ||  z.abs > bailout)
          iter
        else
          run(z * z + c, iter + 1)

      run(Complex.zero, 0)
    }

    def getColor(iter: Int): Color = {
      if (iter == maxIterations) return Color.Black

      val c = 3 * math.log(iter) / math.log(maxIterations - 1.0)
      if (c < 1) Color.rgb((255 * c).toInt, 0, 0)
      else if(c < 2) Color.rgb(255, (255 * (c - 1)).toInt, 0)
      else Color.rgb(255, 255, (255 * (c -  2)).toInt)
    }
  }
  object MandelbrotAlgo{
    def apply(nrIterations: Int) = new MandelbrotAlgo(nrIterations)
  }

  def computeColor(x: Int, y: Int, frame: Frame, complexRectangle: ComplexRectangle, mandelbrot: MandelbrotAlgo): ZIO[Any, Nothing, ColoredPoint] = for {
    iter  <- ZIO.effectTotal(mandelbrot.iterate(complexRectangle.pixelToComplex(frame)(x, y),  1000))
    color <- ZIO.succeed(mandelbrot.getColor(iter))
  } yield ColoredPoint(x, y, color)

  def program: ZIO[Canvas with Console with Clock, Nothing, Unit] = for {
    start            <- clock.nanoTime
    frame            <- UIO.succeed(Frame())
    complexRectangle <- UIO.succeed(ComplexRectangle(-2, 1, -1, 1))
    mandelbrot       <- UIO.succeed(MandelbrotAlgo(1000))
    coloredPoints    <- ZIO.foreachPar(frame.allPoints) {
                          case (x, y) => computeColor(x, y, frame, complexRectangle, mandelbrot)
                        }
    end              <- clock.nanoTime
    _                <- console.putStr(s"calculated all points; it took ${(end - start) / 1000} μs; coloredPoints = \n${coloredPoints.take(20).mkString("\n")}")
    _                <- ZIO.foreach(coloredPoints)(coloredPoint => canvasCapability.drawPoint(coloredPoint))
  } yield ()


}



import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.layout.HBox
import scalafx.scene.paint.Color._

object ScalaFXHelloWorld extends JFXApp { self =>

  val canvas = new SCanvas(600, 400)

  stage = new PrimaryStage {
    title = "Functional Mandelbrot"

    scene = new Scene {
      fill = Black
      content = new HBox {
        padding = Insets(20)
        children = Seq(
          canvas
        )
      }
    }
  }

  val rts = new DefaultRuntime {}
  val env = new CanvasLive with Console.Live with Clock.Live {
    override val scanvas: SCanvas = self.canvas
  }

  rts.unsafeRun (mandelbrot.program.provide(env))
}