// Copyright(C) 2019 - John A. De Goes. All rights reserved.

package net.degoes.zio
package essentials

import java.io.File
import java.lang.NumberFormatException
import java.util.concurrent.{Executors, TimeUnit}

import scalaz.zio.Exit.Cause
import scalaz.zio._
import scalaz.zio.clock.Clock
import scalaz.zio.console.Console
import scalaz.zio.internal.{Platform, PlatformLive}
import scalaz.zio.random.Random
import scalaz.zio.system.System

import scala.io.Source

/**
 * `ZIO[R, E, A]` is an immutable data structure that models an effectful program.
 *
 *  - The program requires an environment `R`
 *  - The program may fail with an error `E`
 *  - The program may succeed with a value `A`
 */
object zio_types {

  /**
   * Write the following types in terms of the `ZIO` type.
   */
  /**
   * A program that might fail with an error of type `E` or succeed with a
   * value of type `A`.
   */
  type FailOrSuccess[E, A] = ZIO[Any, E, A]

  /**
   * A program that never fails and might succeed with a value of type `A`
   */
  type Success[A] = ZIO[Any, Nothing, A]

  /**
   * A program that runs forever but might fail with `E`.
   */
  type Forever[E] = ZIO[Any, E, Nothing]

  /**
   * A program that cannot fail or succeed with a value.
   */
  type NeverStops = ZIO[Any, Nothing, Nothing]

  /**
   * Types aliases built into ZIO.
   */
  /**
   * An effect that may fail with a value of type `E` or succeed with a value
   * of type `A`.
   */
  type IO[E, A] = ZIO[Any, E, A]

  /**
   * An effect that may fail with `Throwable` or succeed with a value of
   * type `A`.
   */
  type Task[A] = ZIO[Any, Throwable, A]

  /**
   * An effect that cannot fail but may succeed with a value of type `A`.
   */
  type UIO[A] = ZIO[Any, Nothing, A]

}

object zio_values {

  /**
   * Using `ZIO.succeed` method. Construct an effect that succeeds with the
   * integer `42`, and ascribe the correct type.
   */
  val ioInt: ZIO[Any, Nothing, Int] = ZIO.succeed(42)

  /**
   * Using the `ZIO.succeedLazy` method, construct an effect that succeeds with
   * the (lazily evaluated) specified value and ascribe the correct type.
   */
  lazy val bigList       = (1L to 100000000L).toList
  lazy val bigListString = bigList.mkString("\n")
  val ioString: UIO[String]      = ZIO.succeedLazy(bigListString)

  /**
   * Using the `ZIO.fail` method, construct an effect that fails with the string
   * "Incorrect value", and ascribe the correct type.
   */
  val incorrectVal: IO[String, Nothing] = ZIO.fail("Incorrect Value")

  /**
   * Using the `ZIO.effectTotal` method, construct an effect that wraps Scala
   * `println` method, so you have a pure functional version of print line, and
   * ascribe the correct type.
   */
  def putStrLn(line: String): UIO[Unit] = ZIO.effectTotal(println(line))

  /**
   * Using the `ZIO.effect` method, wrap Scala's `readLine` method to make it
   * purely functional with the correct ZIO error type.
   *
   * Note: You will have to use the `.refineOrDie` method to refine the
   * `Throwable` type into something more specific.
   *
   * If I don't catch the right exception, the thread it is running on is killed
   */
  import java.io.IOException
  val getStrLn1: Task[String] = ZIO.effect(scala.io.StdIn.readLine())
  val getStrLn: IO[IOException, String] = ZIO.effect(scala.io.StdIn.readLine()).refineOrDie {
    case io: IOException => io
  }

  /**
   * Using the `ZIO.effect` method, wrap Scala's `getLines` to make it
   * purely functional with the correct ZIO error type.
   *
   * Note: You will have to use the `.refineOrDie` method to refine the
   * `Throwable` type into something more specific.
   */
  def readFile(file: File): IO[Throwable, List[String]] = ZIO.effect(
    Source.fromFile(file).getLines.toList
  )

  /**
   * Using the `ZIO.effect` method, wrap Scala's `Array#update` method to make
   * it purely functional with the correct ZIO error type.
   *
   * Note: You will have to use the `.refineOrDie` method to refine the
   * `Throwable` type into something more specific.
   */
  def arrayUpdate[A](a: Array[A], i: Int, f: A => A): IO[String, Unit] =
    ZIO.effect(a.update(i, f(a(i)))).refineOrDie {
      case _ => "error"
    }

  /**
   * In order to execute the effectful programs that are described in `ZIO`
   * values, you need to interpret them using the `Runtime` in `ZIO`
   * and call `unsafeRun`
   *       or
   * call the main function in `zio.App`
   */
  object Example extends DefaultRuntime {
    val sayHelloIO: UIO[Unit] = putStrLn("Hello ZIO!")
    //run sayHelloIO using `unsafeRun`
    val sayHello: Unit = unsafeRun(sayHelloIO)
  }
}

/**
 * Basic operations in zio
 */
object zio_composition {

  /**
   * Map a ZIO value that produces an Int 42 into ZIO value that produces a String "42"
   * by converting the integer into its string
   * and define the return ZIO type
   */
  val toStr: UIO[String] = IO.succeed(42).map(_.toString)

  /**
   * Add one to the value of the computation, and define the return ZIO type
   */
  def addOne(i: Int): UIO[Int] = IO.succeed(i).map(_ + 1)

  /**
   * Map a ZIO value that fails with an Int 42 into ZIO value that fails with a String "42"
   * by converting the integer into its string
   * and define the return ZIO type
   */
  val toFailedStr: IO[String, Nothing] = IO.fail(42).mapError(_.toString)

  /**
   * Using `flatMap` check the precondition `p` in the result of the computation of `io`
   * and improve the ZIO types in the following input parameters
   */
  def verify(io: UIO[Int])(p: Int => Boolean): IO[String, Int] =
    io.flatMap { i =>
      if (p(i)) IO.succeed(i) else IO.fail("precondition failed")
    }

  /**
   * Using `flatMap` and `map` compute the sum of the values of `a` and `b`
   * and improve the ZIO types
   */
  val a: UIO[Int]   = IO.succeed(14)
  val b: UIO[Int]   = IO.succeed(16)
  val sum: IO[Nothing, Int] = a.zipWith(b)(_ + _)

  /**
   * Using `flatMap`, implement `ifThenElse`, which checks the ZIO condition and
   * returns the result of either `ifTrue` or `ifFalse`.
   *
   * @example
   * val exampleIf: IO[String, String] =
   *      ifThenElse(IO.succeed(true))(ifTrue = IO.succeed("It's true!"), ifFalse = IO.fail("It's false!"))
   */
  def ifThenElse[E, A](condition: IO[E, Boolean])(ifTrue: IO[E, A], ifFalse: IO[E, A]): IO[E, A] =
    for {
      cond <- condition
      o <- if (cond) ifTrue else ifFalse
    } yield o

  /**
   * Implement `divide` using `ifThenElse`.
   * if (b != 0), returns `a / b` otherwise, fail with `ArithmeticException`.
   */
  def divide(a: Int, b: Int): IO[ArithmeticException, Int] =
    ifThenElse(IO.succeed(b != 0))(IO.succeed(a / b), IO.fail(new ArithmeticException()))

  /**
   * Using `ifThenElse` implement parseInt that
   * checks if the input is positive, and returns it if it is positive
   * otherwise the program fails with `String` ("This is a negative int") error
   * and define the return ZIO type
   */
  def positive(value: Int): IO[String, Int] = ifThenElse(IO.succeed(value > 0))(IO.succeed(value), IO.fail("This is a negative int"))

  /**
   * Translate this a recursive function that repeats an action n times
   */
  def repeatN1(n: Int, action: () => Unit): Unit =
    if (n <= 0) ()
    else {
      action()
      repeatN1(n - 1, action)
    }

  def repeatN2[E](n: Int, action: IO[E, Unit]): IO[E, Unit] =
    IO.collectAll(List.fill(n)(action)).void

  /**
   * translate the following loop into its ZIO equivalent
   * and improve the ZIO input/output types.
   */
  def sumList1(ints: List[Int], acc: Int): Int = ints match {
    case Nil     => acc
    case x :: xs => sumList1(xs, acc + x)
  }

  def sumList2(ints: IO[Nothing, List[Int]], acc: IO[Nothing, Int]): IO[Nothing, Int] = for {
    xxs <- ints
    a   <- acc
    out <- xxs match {
      case x :: xs => sumList2(IO.succeed(xs), IO.succeed(a + x))
      case Nil => acc
    }
  } yield out

  /**
   * translate the following loop into its ZIO equivalent
   * and improve the ZIO input/output types.
   */
  def decrementUntilFour1(int: Int): Unit =
    if (int <= 4) ()
    else decrementUntilFour1(int - 1)
  def decrementUntilFour2(int: Int): IO[Nothing, Unit] =
    if (int <= 4) IO.succeed(())
    else decrementUntilFour2(int - 1)

  /**
   * Implement the following loop into its ZIO equivalent.
   */
  def factorial(n: Int): Int =
    if (n <= 1) 1
    else n * factorial(n - 1)

  def factorialIO(n: Int): UIO[Int] =
    if (n <= 1) IO.succeed(1)
    else for {
      `n_1` <- factorialIO(n - 1)
    } yield n * `n_1`

  /**
   * Make `factorialIO` tail recursion
   */
  def factorialTailIO(n: Int, acc: Int = 1): UIO[Int] = if (n == 0) UIO.succeed(acc) else factorialTailIO(n - 1, acc * n)

  /**
   * Translate the following program that uses `flatMap` and `map` into
   * its equivalent using for-comprehension syntax sugar
   */
  def multiply(a: UIO[Int], b: UIO[Int]): UIO[Int] = //a.flatMap(v1 => b.map(v2 => v1 * v2))
    for {
      aa <- a
      bb <- b
    } yield aa * bb

  /**
   * Translate the following program, which uses for-comprehension, to its
   * equivalent chain of `flatMap` and `map`
   * improve the ZIO return type
   */
  val totalStr1: IO[Nothing, String] = for {
    v1 <- IO.succeed(42)
    v2 <- IO.succeed(58)
    v3 <- IO.succeed("The Total Is: ")
  } yield v3 + (v1 + v2).toString


  val totalStr2: UIO[String] = IO.succeed(42).flatMap(
    v1 => IO.succeed(58).flatMap(
      v2 => IO.succeed("The Total Is: ").map(
        v3 => v3 + (v1 + v2).toString)
    )
  )

  /**
   * Using `zip`
   * combine the result of two effects into a tuple
   */
  def toTuple(io1: UIO[Int], io2: UIO[Int]): UIO[(Int, Int)] = io1.zip(io2)

  /**
   * Using `zipWith`, add the two values computed by these effects.
   */
  val combine: UIO[Int] = UIO.succeed(2).zipWith(UIO.succeed(40))(_ + _)

  /**
   * Using `ZIO.foreach`
   * convert a list of integers into a List of String
   */
  def convert(l: List[Int]): UIO[List[String]] = ZIO.foreach(l)(a => UIO.succeed(a.toString))

  /**
   * Using `ZIO.collectAll`
   * evaluate a list of effects and collect the result into an IO of a list with their result
   */
  def collect(effects: List[UIO[Int]]): UIO[List[Int]] = ZIO.collectAll(effects)

  /**
   * rewrite this procedural program into a ZIO equivalent and improve the ZIO input/output types
   */
  def analyzeName1(first: String, last: String): String =
    if ((first + " " + last).length > 20) "Your full name is really long"
    else if ((first + last).contains(" ")) "Your name is really weird"
    else "Your name is pretty normal"

  def analyzeName2(first: UIO[String], last: UIO[String]): IO[String, String] = for {
    f <- first
    l <- last
    o <- if ((f + " " + l).length > 20) IO.fail("Your full name is really long")
         else if ((f + l).contains(" ")) IO.fail("Your name is really weird")
         else IO.succeed("Your name is pretty normal")
  } yield o

  /**
   * Translate the following procedural program into ZIO.
   */
  def playGame1(): Unit = {
    val number = scala.util.Random.nextInt(5)
    println("Enter a number between 0 - 5: ")
    scala.util.Try(scala.io.StdIn.readLine().toInt).toOption match {
      case None =>
        println("You didn't enter an integer!")
        playGame1()
      case Some(guess) if guess == number =>
        println("You guessed right! The number was " + number)
      case _ =>
        println("You guessed wrong! The number was " + number)
    }
  }

  lazy val playGame2: Task[Unit] = {
    lazy val getNumber: Task[Int] = for {
      _      <- Task.effect(println("Enter a number between 0 - 5: "))
      input  <- Task.effect(scala.io.StdIn.readLine())
      nr     <- Task.effect(input.toInt) orElse
                (Task.effect(println("You didn't enter an integer!")) *> getNumber)
    } yield nr

    for {
      number <- Task.effect(scala.util.Random.nextInt(5))
      guess  <- getNumber
      _      <- Task.effect(println(
          if (guess == number) "You guessed right! The number was " + number
          else "You guessed wrong! The number was " + number
        )
      )
    } yield ()
  }
}

object zio_failure {

  /**
   * Using `ZIO.fail` method, create an `IO[String, Int]` value that
   * represents a failure with a string error message, containing
   * a user-readable description of the failure.
   */
  val stringFailure1: IO[String, Int] = IO.fail("Something went wrong")

  /**
   * Using the `IO.fail` method, create an `IO[Int, String]` value that
   * represents a failure with an integer error code.
   */
  val intFailure: IO[Int, String] = IO.fail(400)

  /**
   * Translate the following exception-throwing program into its ZIO equivalent.
   * And identify the ZIO types
   */
  def accessArr1[A](i: Int, a: Array[A]): A =
    if (i < 0 || i >= a.length)
      throw new IndexOutOfBoundsException(s"The index $i is out of bounds [0, ${a.length} )")
    else a(i)

  def accessArr2[A](i: Int, a: Array[A]): IO[IndexOutOfBoundsException, A] =
    if (i < 0 || i >= a.length)
    IO.fail(new IndexOutOfBoundsException(s"The index $i is out of bounds [0, ${a.length} )"))
    else IO.succeed(a(i))

  /**
   * Translate the following ZIO program into its exception-throwing equivalent.
   */
  def divide1(n: Int, d: Int): IO[ArithmeticException, Int] =
    if (d == 0) IO.fail(new ArithmeticException)
    else IO.succeedLazy(n / d)

  def divide2(n: Int, d: Int): Int =
    if (d == 0)  throw new ArithmeticException
    else  n / d

  /**
   * Recover from a division by zero error by using `fold`
   */
  val recovered1: UIO[Option[Int]] = divide1(100, 0).fold(_ => None, Some(_)) ?

  /**
   * Using `foldM`, Print out either an error message or the division.
   */
  val recovered2: UIO[Unit] =
    divide1(100, 0).foldM(
      error => UIO.effectTotal(println(error.getMessage)),
      n => UIO.effectTotal(println(n))
    )

  /**
   * Recover from division by zero error by returning -1 using `either`
   */
  val recovered3: UIO[Either[ArithmeticException, Int]] = divide1(100, 0).either

  /**
   * Recover from division by zero error by returning -1 using `option`
   */
  val recovered4: UIO[Option[Int]] = divide1(100, 0).option

  /**
   * Use the `orElse` method of `IO` to try `firstChoice`, and fallback to
   * `secondChoice` only if `firstChoice` fails.
   */
  val firstChoice: IO[ArithmeticException, Int] = divide1(100, 0)
  val secondChoice: UIO[Int]                    = IO.succeed(400)
  val combined: UIO[Int]                        = firstChoice orElse secondChoice

  // io.catchSome
  // io.catchAll

  /**
   * Using `IO.effectTotal`. Import a synchronous effect with a strange code
   * This results in the fiber being terminated
   */
  val defect1: UIO[Int] = IO.effectTotal("this is a short text".charAt(30))

  /**
   * Throw an Exception in pure code using `IO.succeedLazy`.
   * This results in the fiber being terminated
   */
  val defect2: UIO[Int] = IO.succeedLazy(throw new Exception("oh no!"))

  /**
   * Using `die`, terminate this program with a fatal error.
   */
  val terminate1: UIO[Int] = IO.die(new Throwable("Fatal!"))

  /**
   * Using `orDieWith`, terminate the following UIO program with a Throwable
   * when there it fails
   */
  val terminate2: UIO[Int] = IO.fail("unexpected error").orDieWith(new Exception(_))

  /**
   * Using `orDie`, terminate the following UIO program that fails with an exception
   */
  val terminate3: UIO[Int] = IO.effect("Hello".toInt).orDie

  case class DomainError()

  val veryBadIO: IO[DomainError, Int] =
    IO.effectTotal(5 / 0).flatMap(_ => IO.fail(DomainError()))

  /**
   * using `sandbox` recover the `veryBadIO` and catch all errors using `catchAll`
   */
  val caught1: IO[DomainError, Int] = veryBadIO.sandbox.catchAll {
    case Cause.Die(_)     => IO.succeed(0)
    case Cause.Fail(e)    => IO.fail(e)
    case _  => IO.succeed(0)
  }

  /**
   * using `sandboxWith` improve the code above and use `catchSome` with a partial match for the possible errors
   */
  def caught2: IO[DomainError, Int] = veryBadIO.sandboxWith[Any, DomainError, Int] { _.catchSome {
        case Cause.Die(_: ArithmeticException) =>
          // Caught defect: divided by zero!
          IO.succeed(0)
      }
  }

}

object zio_effects {

  /**
   * Using `ZIO.effectTotal` method. increment the given int and identify the correct ZIO type
   */
  def increment(i: Int): UIO[Int] = UIO.effectTotal(i + 1)

  /**
   * Using `ZIO.effect` method. wrap Scala's `toInt` method to convert a given string value to int
   * and identify the correct ZIO type, choose which type alias better
   */
  def parseInt(str: String): Task[Int] = Task.effect(str.toInt)

  /**
   * Using `flatMap` and `map`
   * implement a program that reads from the console and
   * calls `parseInt` to convert the given input into to an integer value
   * and define the return ZIO type
   */
  def readInt: Task[Int] = Task.effect(scala.io.StdIn.readLine()).flatMap( s => parseInt(s).map(i => i))

  /**
   * Translate `readInt` using for-comprehension
   */
  def _readInt: Task[Int] = for {
    s <- Task.effect(scala.io.StdIn.readLine())
    i <- parseInt(s)
  } yield i

  /**
   * Using `catchSome` on `parseInt`, return -1 when
   * the parsing fails with the specified Exception `NumberFormatException` and return a -1
   * and identify the correct ZIO type
   */
  def successfulParseInt(str: String): Task[Int] = Task.effect(scala.io.StdIn.readLine()).flatMap(parseInt).catchSome {
    case e: NumberFormatException => Task.succeed(-1)
  }

  /**
   * Using `catchAll` method, wrap Scala's `getLines` method to
   * import it into the world of pure functional programming and recover from all Exceptions
   * to return a successful result with an empty list
   */
  def readFile(file: File): UIO[List[String]] =
    IO.effect(Source.fromFile(file).getLines.toList).catchAll(_ => UIO.succeed(List()))

  /**
   * Using `refineOrDie` method, catch the NoSuchElementException and return -1
   * piero: this has no solution with `refineOrDie` as refine is not to recover errors, it is to transform errors
   */
  def first(as: List[Int]): Task[Int] = Task.effect(as.head) orElse Task.succeed(-1)

  /**
   * Use `ZIO.effectAsync` method to implement the following `sleep`method
   * and choose the correct error type
   */
  val scheduledExecutor = Executors.newScheduledThreadPool(1)

  //this sleeps without blocking anything. It's _semantically blocking_
  def sleep(l: Long, u: TimeUnit): UIO[Unit] = UIO.effectAsync(
    k =>
      scheduledExecutor
        .schedule( new Runnable {
          def run(): Unit = k(UIO.unit)
        }, l, u)
    )

  val interleaved = for {
    _ <- sleep(1, TimeUnit.SECONDS)
    _ <- Task(println("awake!"))
    _ <- sleep(1, TimeUnit.SECONDS)
    _ <- Task(println("awake again!"))
    _ <- sleep(1, TimeUnit.SECONDS)
    _ <- Task(println("awake again and again!"))
  } yield ()

  /**
   * Wrap the following Java callback API, into an `IO` using `IO.effectAsync`
   * and use other ZIO type alias
   */
  def readChunkCB(success: Array[Byte] => Unit, failure: Throwable => Unit): Unit = ???
  val readChunkIO: IO[Throwable, Array[Byte]]                                     = IO.effectAsync { k =>
    readChunkCB(arr => k(IO.succeed(arr)), err => k(IO.fail(err)))
  }

  /**
   * Rewrite this program using `readChunkIO`
   */
  readChunkCB(
    a1 =>
      readChunkCB(
        a2 => readChunkCB(a3 => println(s"${a1 ++ a2 ++ a3}"), e3 => println(s"${e3.toString}")),
        e2 => println(s"${e2.toString}")
      ),
    e1 => println(s"${e1.toString}")
  )

  (for {
    a1 <- IO.collectAll(List.fill(3)(readChunkIO)) // this is amazing stuff!
//    a2 <- readChunkIO
//    a3 <- readChunkIO
    _ <- Task.effect(println(a1.mkString("")))
  } yield  ()).catchAll(error => Task.effect(println(s"${error.toString}")))

  /**
   * Using `ZIO.effectAsyncMaybe` wrap the following Java callback API into an `IO`
   * and use better ZIO type alias.
   */
  def readFromCacheCB(key: String)(success: Array[Byte] => Unit, error: Throwable => Unit): Option[Array[Byte]] = ???
  def readFromCacheIO(key: String): IO[Throwable, Array[Byte]]                                                  = IO.effectAsyncMaybe(
    k => readFromCacheCB(key)(arr => k(IO.succeed(arr)), err => k(IO.fail(err))).map(IO.succeed)
  )

  /**
   * using `ZIO.effectAsyncInterrupt` wrap the following Java callback API into an `IO`
   * and use better ZIO type alias.
   */
  case class HttpGetToken(canceller: () => Unit)

  def httpGetCB(url: String)(success: Array[Byte] => Unit, error: Throwable => Unit): HttpGetToken = ???
  def httpGetIO(url: String): IO[Throwable, Array[Byte]] =
    IO.effectAsyncInterrupt { k =>
      Left(UIO(httpGetCB(url)(arr => k(IO.succeed(arr)), err => k(IO.fail(err)))))
    }

  /**
   * using `ZIO.effectAsync` import a pure value of type int
   * and identify the ZIO type
   */
  val async42: UIO[Int] = ZIO.effectAsync(k => k(ZIO.succeed(42)))
}

object impure_to_pure {

  /**
   * Translate the following procedural programs into ZIO.
   */
  def getName1(print: String => Unit, read: () => String): Option[String] = {
    print("Do you want to enter your name?")
    read().toLowerCase.take(1) match {
      case "y" => Some(read())
      case _   => None
    }
  }
  def getName2[E](print: String => IO[E, Unit], read: IO[E, String]): IO[E, Option[String]] =
    for {
      _ <- print("Do you want to enter your name?")
      s <- read.map(_.toLowerCase)
      o <- s.take(1) match {
        case "y" => read.map(Some(_))
        case _   => UIO.succeed(None)
      }
    } yield o

  def ageExplainer1(): Unit = {
    println("What is your age?")
    scala.util.Try(scala.io.StdIn.readLine().toInt).toOption match {
      case Some(age) =>
        if (age < 12) println("You are a kid")
        else if (age < 20) println("You are a teenager")
        else if (age < 30) println("You are a grownup")
        else if (age < 50) println("You are an adult")
        else if (age < 80) println("You are a mature adult")
        else if (age < 100) println("You are elderly")
        else println("You are probably lying.")
      case None =>
        println("That's not an age, try again")

        ageExplainer1()
    }
  }

  def ageExplainer2: UIO[Unit] = {
    def process(age: Int): String = if (age < 12) "You are a kid"
    else if (age < 20) "You are a teenager"
    else if (age < 30) "You are a grownup"
    else if (age < 50) "You are an adult"
    else if (age < 80) "You are a mature adult"
    else if (age < 100) "You are elderly"
    else "You are probably lying."


    (for {
      _ <- UIO.effectTotal(println("What is your age?"))
      input <- Task.effect(scala.io.StdIn.readLine())
      age <- Task.effect(input.toInt)
      _ <- Task.effect(println(process(age)))
    } yield ()).catchAll(_ => UIO.effectTotal(println("That's not an age, try again")) *> ageExplainer2)
  }

  def decode1(read: () => Byte): Either[Byte, Int] = {
    val b = read()
    if (b < 0) Left(b)
    else {
      Right(
        b.toInt +
          (read().toInt << 8) +
          (read().toInt << 16) +
          (read().toInt << 24)
      )
    }
  }
  def decode2[E](read: IO[E, Byte]): IO[E, Either[Byte, Int]] = read.flatMap { r =>
    if (r < 0) IO.succeed(Left(r))
    else IO.collectAll(List.fill(3)(read)).map {
      case List(a, b, c) => Right(r + (a.toInt << 8) + (b.toInt << 16) + (c.toInt << 24))
    }
  }

  def replaceV1(as: List[Int], value: Int, newValue: Int): List[Int] =
    as.map(v => if (v == value) newValue else v)

  def replaceV2(as: List[UIO[Int]], value: UIO[Int], newValue: UIO[Int]): UIO[List[Int]] = for {
    v <- value
    res  <- IO.foreach(as)(a => a.flatMap(x => if (x == v) newValue else UIO.succeed(x)))
  } yield res
}

object zio_interop {

  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.global

  /**
   * Using `Fiber#toFuture`. Convert the following `Fiber` into a `Future`
   */
  val fiber: Fiber[Throwable, Int] = Fiber.succeed(1)
  val fToFuture: UIO[Future[Int]]       = fiber.toFuture

  /**
   * Using `Fiber.fromFruture`. Wrap the following Future in a `Fiber`
   */
  val future1                          = () => Future(Thread.sleep(1000))(global)
  val fToFiber: Fiber[Throwable, Unit] = Fiber.fromFuture(future1())

  /**
   * Using `Task#toFuture`. Convert unsafely the following ZIO Task into `Future`.
   */
  val task1: Task[Int]       = IO.effect("wrong".toInt)
  val tToFuture: Future[Int] = new DefaultRuntime{}.unsafeRun(task1.toFuture)

  /**
   * Use `Task.fromFuture` to convert the following Scala `Future` into ZIO Task
   */
  val future2             = () => Future.successful("Hello World")
  val task2: Task[String] = Task.fromFuture(_ => future2() )

}

trait Logger {
  def log(line: String): Unit
}

object LoggerEx {
  trait LoggerIO {
    def log(line: String): UIO[Unit]
  }

  object LoggerIO {
    def fromLogger(logger: Logger): LoggerIO = new LoggerIO {
      override def log(line: String): UIO[Unit] = UIO.effectTotal(logger.log(line))
    }
  }

  val logger: LoggerIO = ???

  for {
    _ <- logger.log("asdfad")
  } yield ()

}

object zio_resources {
  import java.io.{ File, FileInputStream }
  class InputStream private (is: FileInputStream) {
    def read: IO[Exception, Option[Byte]] =
      IO.effectTotal(is.read).map(i => if (i < 0) None else Some(i.toByte))
    def close: IO[Exception, Unit] =
      IO.effectTotal(is.close())
  }
  object InputStream {
    def openFile(file: File): IO[Exception, InputStream] =
      IO.effectTotal(new InputStream(new FileInputStream(file)))
  }

  /**
   * This following program is the classic paradigm for resource handling using try / finally
   */
  object classic {
    trait Handle
    def openFile(file: String): Handle        = ???
    def closeFile(handle: Handle): Unit       = ???
    def readFile(handle: Handle): Array[Byte] = ???

    // Classic paradigm for safe resource handling using
    // try / finally:
    def safeResource(file: String): Unit = {
      var handle: Handle = null.asInstanceOf[Handle]

      try {
        handle = openFile(file)

        readFile(handle)
      } finally if (handle != null) closeFile(handle)
    }

    def finallyPuzzler(): Unit =
      try {
        try throw new Error("e1")
        finally throw new Error("e2")
      } catch {
        case e: Error => println(e)
      }
  }

  /**
   * Rewrite the following procedural program to ZIO, using `IO.fail` and the `ensuring` method.
   */
  var i = 0
  def increment1(): Unit =
    try {
      i += 1
      throw new Exception("Boom!")
    } finally i -= 1

  def increment2(): Task[Unit] =
    Task.effect(i += 1)
      .flatMap(_ => Task.fail(new Exception("Boom!")))
      .ensuring(Task.succeedLazy(i -= 1))

  /**
   * Rewrite the following procedural program to ZIO, using `IO.fail` and the
   * `ensuring` method of the `IO` object.
   */
  def tryCatch1(): Unit =
    try throw new Exception("Uh oh")
    finally println("On the way out...")
  val tryCatch2: Task[Unit] = IO.fail(new Exception("Uh oh")).ensuring(UIO.succeedLazy(println("On the way out...")))

  /**
   * Rewrite the `readFile1` function to use `bracket` so resources can be
   * safely cleaned up in the event of errors, defects, or interruption.
   */
  def readFile1(file: File): Task[List[Byte]] = {
    def readAll(is: InputStream, acc: List[Byte]): Task[List[Byte]] =
      is.read.flatMap {
        case None       => IO.succeed(acc.reverse)
        case Some(byte) => readAll(is, byte :: acc)
      }

    for {
      stream <- InputStream.openFile(file)
      bytes  <- readAll(stream, Nil)
      _      <- stream.close
    } yield bytes
  }

  def readFile2(file: File): IO[Exception, List[Byte]] = {
    def readAll(is: InputStream, acc: List[Byte]): IO[Exception, List[Byte]] =
      is.read.flatMap {
        case None => IO.succeed(acc.reverse)
        case Some(byte) => readAll(is, byte :: acc)
      }

    InputStream.openFile(file)
      .bracket { is =>
        is.close orElse UIO(())
      } { use =>
        readAll(use, List.empty[Byte])
      }
  }

  /**
   * Implement the `tryCatchFinally` method using `bracket` or `ensuring`.
   */
  def tryCatchFinally[E, A](try0: IO[E, A])(catch0: PartialFunction[E, IO[E, A]])(finally0: UIO[Unit]): IO[E, A] =
//    try0.ensuring(finally0).catchSome(catch0)
    IO.bracket(try0.catchSome(catch0))(_ => finally0)(IO.succeed)

  /**
   * Use the `bracket` method to rewrite the following snippet to ZIO.
   */
  def readFileTCF1(file: File): List[Byte] = {
    var fis: FileInputStream = null

    try {
      fis = new FileInputStream(file)
      val array = Array.ofDim[Byte](file.length.toInt)
      fis.read(array)
      array.toList
    } catch {
      case e: java.io.IOException => Nil
    } finally if (fis != null) fis.close()
  }
  def readFileTCF2(file: File): Task[List[Byte]] = IO.bracket(IO.effect(new FileInputStream(file)))(fis => UIO.succeedLazy(fis.close())){ fis =>
    (for {
      array <- IO.effect(Array.ofDim[Byte](file.length.toInt))
      _     <- IO.effect(fis.read(array))
    } yield array.toList) orElse UIO.succeedLazy(Nil)
  }

  /**
   *`Managed[R, E, A]` is a managed resource of type `A`, which may be used by
   * invoking the `use` method of the resource. The resource will be automatically
   * acquired before the resource is used, and automatically released after the
   * resource is used.
   */
  /**
   * separate: the acquire, release and the use actions in this procedural program
   * and describe them as ZIO data structures
   */
  trait Status
  object Status {
    case object Opened extends Status
    case object Closed extends Status
  }
  case class Charge(price: Double)
  def buyTicket(ch: Charge) = ???

  var status: Status = Status.Opened
  def receiveEvent(ev: Charge) =
    try {
      if (status == Status.Closed)
        Status.Opened
      else
        buyTicket(ev)
    } finally status = Status.Closed

  /**
   * Define a value of type `Managed` for the acquire and release actions
   * and identify the correct types
   */
  val managed1: Managed[Any, Throwable, Status] = ???
//    Managed.make(ZIO.effect(status = {Status.Opened; status}))(_ => IO.succeedLazy(status = Status.Closed))

  /**
   * implement the computation of `use` for this Managed
   */
  val use1: IO[???, ???] = ???
  /**
   * write the same example using `manage` in ZIO for the `acquire` action
   */
  val computation: IO[???, ???] = ???

  /**
   * using the data structure `Managed` implement a program that divide a/b then check if it is odd,
   * and finally print out the result on the console
   */
  def acquire(a: Int, b: Int): Task[Int]    = (a / b) ?
  val managed: Managed[Any, Throwable, Int] = ??? //Managed.make(acquire(4, 5))(i => UIO.effectTotal(s"i equals $i"))
  val check: Task[Int]                      = ??? //managed.use(n => ZIO.effectTotal(println(s"$n is odd: ${n % 2 == 0}")))

}

object zio_environment {
  //tagless final version
  // https://gitter.im/jdegoes/functional-scala?at=5c7fe7038f294b134afa98a1
  //logging module
  trait Logging {
    def logging: Logging.Service
  }

  object Logging {
    trait Service {
      def log(line: String): UIO[Unit]
    }

    def log(line: String): ZIO[Logging, Nothing, Unit] = ZIO.accessM(r => r.logging.log(line))
  }

//  in the same way another module
//logging module\
  trait ResultSet
  trait Database {
    def database: Database.Service
  }

  object Database {
    trait Service {
      def query(q: String): UIO[ResultSet]
    }

    def query(q: String): ZIO[Database, Nothing, ResultSet] = ZIO.accessM(r => r.database.query(q))
  }
  def program: ZIO[Logging with Database, Nothing, Unit] = for {
    _ <- Logging.log("hello")
    result <- Database.query("select * ")
    _ <- Logging.log("hello1")
    _ <- Logging.log("hello2")
  } yield ()

  /**
   * The Environments in ZIO
   * console (putStr, getStr)
   * clock (currentTime, sleep, nanoTime)
   * random (nextInt, nextBoolean, ...)
   * system (env)
   * Blocking
   * Scheduler
   */
  //write the type of a program that requires scalaz.zio.clock.Clock and which could fail with E or succeed with A
  type ClockIO[E, A]  = ZIO[Clock, E, A]

  //write the type of a program that requires scalaz.zio.console.Console and which could fail with E or succeed with A
  type ConsoleIO[E, A] = ZIO[Console, E, A]

  //write the type of a program that requires scalaz.zio.system.System and which could fail with E or succeed with A
  type SystemIO[E, A] = ZIO[System, E, A]

  //write the type of a program that requires scalaz.zio.random.Random and which could fail with E or succeed with A
  type RandomIO[E, A] = ZIO[Random, E, A]

  //write the type of a program that requires Clock and System and which could fail with E or succeed with A
  type ClockWithSystemIO[E, A] = ZIO[Clock with System, E, A]

  //write the type of a program that requires Console and System and which could fail with E or succeed with A
  type ConsoleWithSystemIO[E, A] = ZIO[Console with System, E, A]

  //write the type of a program that requires Clock, System and Random and which could fail with E or succeed with A
  type ClockWithSystemWithRandom[E, A] = ZIO[Clock with System with Random, E, A]

  //write the type of a program that requires Clock, Console, System and Random and which could fail with E or succeed with A
  type ClockWithConsoleWithSystemWithRandom[E, A] = ZIO[Clock with Console with System with Random, E, A]
}

object zio_dependency_management {
  import scalaz.zio.console
  import scalaz.zio.console.Console
  import scalaz.zio.clock
  import scalaz.zio.clock.Clock
  import scalaz.zio.system
  import scalaz.zio.system.System
  import java.io.IOException

  /**
   * Using `zio.console.getStrLn`, implement `getStrLn` and identify the
   * correct type for the ZIO effect.
   */
  val getStrLn: ZIO[Console, IOException, String] = console.getStrLn

  /**
   * Using `zio.console.putStrLn`, implement `putStrLn` and identify the
   * correct type for the ZIO effect.
   */
  def putStrLn(line: String): ZIO[Console, Nothing, Unit] = console.putStr(line)

  /**
   * Using `scalaz.zio.clock.nanoTime`, implement `nanoTime` and identify the
   * correct type for the ZIO effect.
   */
  val nanoTime: ZIO[Clock, Nothing, Long] = clock.nanoTime

  /**
   * Using `scalaz.zio.system`, implement `env` and identify the correct
   * type for the ZIO effect.
   */
  def env(property: String): ZIO[System, SecurityException, Option[String]] = system.env(property)

  /**
   * Call three of the preceding methods inside the following `for`
   * comprehension and identify the correct type for the ZIO effect.
   */
  val program: ZIO[Console with System with Clock, Throwable, Unit] = for {
    _ <- console.putStr("Hello what's your name")
    name <- console.getStrLn
    _ <- console.putStr(s"Nice to meet you $name")
    time <- clock.nanoTime
    _ <- console.putStr(s"The nanotime is $time")
    _ <- console.putStr(s"What system property do you want?")
    propertyName <- console.getStrLn
    property <- system.property(propertyName)
    _ <- console.putStr(s"Property is $property")
  } yield ()


  val runnableProgram = program.provide(new Console.Live with System.Live with Clock.Live)

  /**
   * Build a new Service called `Configuration`
   * - define the module
   * - define the interface
   * - define the helper functions (host, port)
   * - implement a trait `Live` that extends the module.
   * - implement all helper functions.
   */
  //Module
  import system.System

  trait Config {
    val config: Config.Service[Any]
  }

  object Config {
    // Service: definition of the methods provided by module:
    trait Service[R] {
      val port: ZIO[R, Nothing, Int]
      val host: ZIO[R, Nothing, String]
    }
    // Production module implementation:
    trait Live extends Config {
      val config: Config.Service[Any] = new Config.Service[Any] {
        override val port: ZIO[Any, Nothing, Int] = UIO.succeed(8080)

        override val host: ZIO[Any, Nothing, String] = UIO.succeed("localhost")
      }
    }
    object Live extends Live
  }

  //Helpers
  object config_ extends Config.Service[Config] {

    /**
     * Access to the environment `Config` using `accessM`
     */
    override  val port: ZIO[Config, Nothing, Int] = ZIO.accessM(_.config.port)
    override  val host: ZIO[Config, Nothing, String] = ZIO.accessM(_.config.host)
  }

  import config_._

  /**
   * Write a program that depends on `Config` and `Console` and use the Scala
   * compiler to infer the correct type.
   */
  val configProgram: ZIO[Console with Config, Nothing, Unit] = for {
    _ <- console.putStr("Hello, here is the configuration of your server")
    host <- config_.host
    port <- config_.port
    _ <- console.putStr(s"host = $host, port = $port")
  } yield ()

  /**
   * Give the `configProgram` its dependencies by supplying it with both `Config`
   * and `Console` modules, and determine the type of the resulting effect.
   */
  configProgram.provide(new Console.Live with Config.Live)

  /**
   * Create a `Runtime[Config with Console]` that can be used to run any
   * effect that has a dependency on `Config`:
   */
  val ConfigRuntime: Runtime[Config with Console] =
    Runtime(new Console.Live with Config.Live, PlatformLive.Default)

  /**
   * Define a ZIO value that describes an effect which uses Config with
   * Console that displays the port and host in the Console and fails
   * with a String if the host name contains `:`
   */
  val simpleConfigProgram: ZIO[Config with Console, String, Unit] = ZIO.accessM { env =>
    for {
      port <- env.config.port
      host <- env.config.host
      out  <- if (host.contains(":"))
        IO.fail(s"hostname `$host` contains invalid chars")
        else
        UIO.succeed(s"host: $host, port: $port")
    } yield out
  }

  /**
   * run the `program` using `unsafeRun`
   * @note When you call unsafeRun the Runtime will provide all the environment that you defined above
   *       when you give a wrong Environment, you will get compile errors.
   */
  val run: Unit = ConfigRuntime.unsafeRun(simpleConfigProgram)

  /**
   * Build a file system service
   */
  //Module
  trait FileSystem {
    val filesystem: ???
  }

  object FileSystem {
    // Service: definition of the methods of the module:
    trait Service[R] {}

    // Production implementation of the module:
    trait Live extends FileSystem {
      val filesystem: ??? = ???
    }
    object Live extends Live
  }
  //Helpers
  object filesystem_ extends FileSystem.Service[FileSystem] {}

  /**
   * Write an effect that uses `FileSystem with Console`.
   */
  val fileProgram: ZIO[FileSystem with Console, ???, ???] = ???

  /**
   * Create a `Runtime` that can execute effects that require
   * `FileSystem with Console`.
   */
  val FSRuntime: Runtime[FileSystem with Console] = ???

  /**
   * Execute `fileProgram` using `FSRuntime`.
   */
  lazy val fileProgramLive: ??? = FSRuntime.unsafeRun(fileProgram)

  /**
   * Implement a mock file system module.
   */
  trait MockFileSystem extends FileSystem {
    val filesystem = ???
  }

  /**
   * Using `ZIO#provide` with the mock file system module, and a default
   * runtime, execute `fileProgram`.
   */
  lazy val fileProgramTest: ??? = new DefaultRuntime {}.unsafeRun {
    fileProgram.provide(???)
  }
}
