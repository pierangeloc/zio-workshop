// Copyright(C) 2019 - John A. De Goes. All rights reserved.

package net.degoes.zio
package essentials

import scala.annotation.tailrec
import scala.util.Try

object effects {

  /**
   * `Console` is an immutable data structure that describes a console program
   * that may involve reading from the console, writing to the console, or
   * returning a value.
   */
  sealed trait Console[A] { self =>
    import Console._

    /**
     * Implement `flatMap` for every type of `Console[A]` to turn it into a
     * `Console[B]` using the function `f`.
     */
    final def flatMap[B](f: A => Console[B]): Console[B] = self match {
      case Return(a) => f(a())
      case ReadLine(next) => ReadLine { line =>
        next(line).flatMap(f)
      }
      case WriteLine(line, next) => WriteLine(line, next.flatMap(f))
    }

    final def map[B](f: A => B): Console[B] = flatMap(f andThen (Console.succeed(_)))

    final def *>[B](that: Console[B]): Console[B] = (self zip that).map(_._2)

    final def <*[B](that: Console[B]): Console[A] = (self zip that).map(_._1)

    /**
     * Implement the `zip` function using `flatMap` and `map`.
     */
    final def zip[B](that: Console[B]): Console[(A, B)] = self.flatMap(a => that.map(b => (a, b)))
  }

  object Console {
    final case class ReadLine[A](next: String => Console[A])      extends Console[A]
    final case class WriteLine[A](line: String, next: Console[A]) extends Console[A]
    final case class Return[A](value: () => A)                    extends Console[A]

    /**
     * Implement the following helper functions:
     */
    final val readLine: Console[String]              = ReadLine(s => Return(() => s))
    final def writeLine(line: String): Console[Unit] = WriteLine(line, Return(() => ()))
    final def succeed[A](a: => A): Console[A]        = Return( ()=> a)
  }

  /**
   * Using the helper functions, write a program that just returns a unit value.
   */
  val unit: Console[Unit] = Console.succeed(())

  /**
   * Using the helper functions, write a program that just returns the value 42.
   */
  val fortyTwo: Console[Int] = Console.succeed(42)

  /**
   * Using the helper functions, write a program that asks the user for their name.
   */
  val askName: Console[Unit] = Console.writeLine("What's your name?")

  /**
   * Using the helper functions, write a program that read the name of the user.
   */
  val readName: Console[String] = Console.readLine

  /**
   * Using the helper functions, write a program that greets the user by their name.
   */
  def greetUser(name: String): Console[Unit] = Console.writeLine(name)

  /***
   * Using `flatMap` and the preceding three functions, write a program that
   * asks the user for their name, reads their name, and greets them.
   */
  val sayHello: Console[Unit] = for {
    _ <- greetUser("Hello, what's your name?")
    n <- readName
    _ <- greetUser(s"Hello $n, nice to meet you!")
  } yield ()

  /**
   * Write a program that reads from the console then parse the given input into int if it possible
   * otherwise it returns None
   */
  val readInt: Console[Option[Int]] = Console.readLine.map(s => Try(s.toInt).toOption)

  /**
   * implement the following effectful procedure, which interprets
   * the description of a given `Console[A]` into A and run it.
   */
  @tailrec
  def unsafeRun[A](program: Console[A]): A = program match {
    case Console.ReadLine(next) => unsafeRun(next(scala.io.StdIn.readLine()))
    case Console.WriteLine(s, next) =>
      println(s)
      unsafeRun(next)
    case Console.Return(a) => a()
  }

  /**
    * Implement the `foreach` function, which iterates over the values in a list,
    * passing every value to a body, which effectfully computes a `B`, and
    * collecting all such `B` values in a list.
    */

  def collectAll[A](programs: List[Console[A]]): Console[List[A]] = {
    programs.foldLeft[Console[List[A]]](Console.succeed(List[A]())) { (console, program) =>
      for {
        as <- console
        a  <- program
      } yield as :+ a
    }
  }

  /**
   * implement the `foreach` function that compute a result for each iteration
   */
  def foreach[A, B](values: List[A])(body: A => Console[B]): Console[List[B]] =
    collectAll(values.map(body))

  /**
   * Using `Console.writeLine` and `Console.readLine`, map the following
   * list of strings into a list of programs, each of which writes a
   * question and reads an answer.
   */
  val questions =
    List(
      "What is your name?",
      "Where where you born?",
      "Where do you live?",
      "What is your age?",
      "What is your favorite programming language?"
    )
  val answers: List[Console[String]] = questions.map { q =>
    Console.writeLine(q) *> Console.readLine
  }

  /**
   * Using `collectAll`, transform `answers` into a program that returns
   * a list of strings.
   */
  val answers2: Console[List[String]] = collectAll(answers)

  /**
   * Now using only `questions` and `foreach`, write a program that is
   * equivalent to `answers2`.
   */
  val answers3: Console[List[String]] = foreach(questions) { q =>
    Console.writeLine(q) *> Console.readLine
  }

  val printAllAnswers = for {
    ans <- answers3
    _   <- Console.writeLine("answers: \n" + ans.mkString("\n"))
  } yield ()

  /**
   * Implement the methods of Thunk
   */
  class Thunk[A](val unsafeRun: () => A) { self =>
    def map[B](ab: A => B): Thunk[B]             = new Thunk(() => ab(unsafeRun()))
    def flatMap[B](afb: A => Thunk[B]): Thunk[B] = new Thunk(() => afb(unsafeRun()).unsafeRun())
    def attempt: Thunk[Either[Throwable, A]]     = new Thunk(() => Try(unsafeRun()).toEither)
  }
  object Thunk {
    def succeed[A](a: => A): Thunk[A]   = new Thunk(() => a)
    def fail[A](t: Throwable): Thunk[A] = new Thunk(() => throw t)
  }

  /**
   * Build the version of printLn and readLn
   * then make a simple program base on that.
   */
  def printLn(line: String): Thunk[Unit] = new Thunk(() => println(line))
  def readLn: Thunk[String]              = new Thunk(() => scala.io.StdIn.readLine())

  val thunkProgram: Thunk[Unit] = for {
    _ <- printLn("Hello, what's your name?")
    name <- readLn
    _ <- printLn(s"Hi $name, nice to meet you!")
  } yield ()
}
