package net.degoes.zio.applications

import net.degoes.zio.applications.mandelbrot.{Canvas, MandelbrotAlgo}
import scalafx.scene.canvas.{Canvas => SCanvas}
import scalafx.scene.paint.Color
import scalaz.zio.{DefaultRuntime, UIO, ZIO}
import scalaz.zio.console

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
  //canvas component
  sealed trait Canvas {
    val canvas: Canvas.Service[Any]
  }

  object Canvas {
    trait Service[-R] {
      def drawPoint(x: Int, y: Int, color: Color): ZIO[R, Nothing, Unit]
    }

    trait CanvasLive extends Canvas {
      val scanvas: SCanvas
      val canvas = new Canvas.Service[Any] {
        override def drawPoint(x: Int, y: Int, color: Color): ZIO[Any, Nothing, Unit] = {
          def setColor: UIO[Unit] = ZIO.effect(scanvas.graphicsContext2D.setFill(color)).catchAll { e => {
              e.printStackTrace();
              UIO.succeed(())
            }
          }

          def drawOval: UIO[Unit] = ZIO.effectTotal(scanvas.graphicsContext2D.fillOval(x, y, 0.1, 0.1))

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

  object canvas extends Canvas.Service[Canvas] {
    override def drawPoint(x: Int, y: Int, color: Color): ZIO[Canvas, Nothing, Unit] = ZIO.accessM(_.canvas.drawPoint(x, y, color))
  }

  case class Frame(width: Int = 400, height: Int = 600) {
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


  //an Int complex, as we are only interested in * and +
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

    def iterate(c: Complex, bailout:Int): Int = {

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

//  def getCanvas: UIO[Canvas] = ???
//  def colorComplex(z: Complex, color: Color, canvas: Canvas): UIO[Unit] = ZIO.effectTotal {
//    val gc = canvas.graphicsContext2D
//    gc.fillOval()
//
//  }

  def computeColor(x: Int, y: Int, frame: Frame, complexRectangle: ComplexRectangle, mandelbrot: MandelbrotAlgo): ZIO[Any, Nothing, (Int, Int, Color)] = for {
    iter  <- ZIO.effectTotal(mandelbrot.iterate(complexRectangle.pixelToComplex(frame)(x, y),  1000))
    color <- ZIO.succeed(mandelbrot.getColor(iter))
  } yield (x, y, color)


  def computeAndShow(x: Int, y: Int, frame: Frame, complexRectangle: ComplexRectangle, mandelbrot: MandelbrotAlgo): ZIO[Canvas, Nothing, Unit] = for {
    iter  <- ZIO.effectTotal(mandelbrot.iterate(complexRectangle.pixelToComplex(frame)(x, y),  1000))
    color <- ZIO.succeed(mandelbrot.getColor(iter))
    _     <- canvas.drawPoint(x, y, color)
  } yield ()

  def program: ZIO[Canvas, Nothing, Unit] = for {
    frame            <- UIO.succeed(Frame(400, 600))
    complexRectangle <- UIO.succeed(ComplexRectangle(-2, 1, -1, 1))
    mandelbrot       <- UIO.succeed(MandelbrotAlgo(1000))
    _                <- ZIO.foreachPar(frame.allPoints) {
                          case (x, y) => computeAndShow(x, y, frame, complexRectangle, mandelbrot)
//                          case (x, y) => computeColor(x, y, frame, complexRectangle, mandelbrot)
                        }
  } yield ()


}



import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.effect.DropShadow
import scalafx.scene.layout.HBox
import scalafx.scene.paint.Color._
import scalafx.scene.paint.{LinearGradient, Stops}
import scalafx.scene.text.Text

object ScalaFXHelloWorld extends JFXApp {

  val canvas = new SCanvas(400, 600)

  stage = new PrimaryStage {
    title = "ScalaFX Hello World"
//    val gc = canvas.graphicsContext2D
//    gc.fill = Color.Green
//    for {
//      x <- (0 to 600)
//      y <- (0 to 700)
//    } yield gc.fillOval(x, y, 1, 1)

//    gc.stroke = Color.Blue
//    gc.lineWidth = 5
//    gc.strokeLine(40, 10, 10, 40)

    scene = new Scene {
      fill = Black
      content = new HBox {
        padding = Insets(20)
        children = Seq(
          canvas,
          new Text {
            text = "Hello "
            style = "-fx-font-size: 48pt"
            fill = new LinearGradient(
              endX = 0,
              stops = Stops(PaleGreen, SeaGreen))
          },
          new Text {
            text = "World!!!"
            style = "-fx-font-size: 48pt"
            fill = new LinearGradient(
              endX = 0,
              stops = Stops(Cyan, DodgerBlue)
            )
            effect = new DropShadow {
              color = DodgerBlue
              radius = 25
              spread = 0.25
            }
          }
        )
      }
    }
  }

  val rts = new DefaultRuntime {}
  rts.unsafeRun (mandelbrot.program.provide(Canvas.CanvasLive.withScanvas(canvas)))
}