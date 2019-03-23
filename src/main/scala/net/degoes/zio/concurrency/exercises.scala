// Copyright(C) 2019 - John A. De Goes. All rights reserved.

package net.degoes.zio
package concurrency

import java.io.IOException
import java.time.LocalDate

import scalaz.zio._
import scalaz.zio.clock.Clock
import scalaz.zio.console.{Console, putStrLn}
import scalaz.zio.duration._
import scalaz.zio.stream.{Sink, ZStream}


object zio_fibers {

  /**
   * A Fiber is a lightweight Thread of execution
   * They are spawned by forking an IO value, they take the types of the IO
   * every value in ZIO is described in IO
   * ZIO#fork describe a computation that never fails and produces a computation in a separated Fiber
   * That helps us to build concurrent and non blocking programs
   */
  /**
   * Using `ZIO#fork` Start a task that fails with "oh no!" in a separated Fiber
   * Identify the correct types
   */
  val failedF: UIO[Fiber[String, Nothing]] = IO.fail("oh no!").fork

  /**
   *  Using `ZIO#fork` Start a task that produces an int in a separated Fiber
   * Identify the correct types
   */
  val succeededF: UIO[Fiber[Nothing, Int]] = IO.succeed(1).fork

  /**
   *  Using `ZIO#fork` Start a `console.putStrLn` in a new fiber
   *  Identify the correct types (!)
   */
  val putStrLnF: ZIO[Console, Nothing, Fiber[Nothing, Unit]] = console.putStrLn("Hello ZIO").fork

  /**
   *  Print in the console forever without blocking the main Fiber
   *  Identify the correct types (!)
   */
  val putStrLForeverF: ZIO[Console, Nothing, Fiber[Nothing, Unit]] = {

    console.putStrLn("Hello ZIO").forever.fork
  }

  val putStrLForeverAndHijackMain: ZIO[Console, Nothing, Unit] = console.putStrLn("Hello ZIO on main fiber").forever

  /**
   * Get the value of the following Fibers using `Fiber#join`
   * Identify the correct types.
   */
  val fiber1: Fiber[Nothing, Int] = Fiber.succeed[Nothing, Int](1)
  val fiber2: Fiber[Int, Nothing] = Fiber.fail[Int](1)
  val fiber3: Fiber[Exception, Nothing] = Fiber.fail[Exception](new Exception("error!"))

  val joined = for {
    f1 <- fiber1.join
    f2 <- fiber2.join
    f3 <- fiber3.join
  } yield (f1, f2, f3)

  /**
   * Using `await` suspend the awaiting fibers until the result will be ready and
   * returns if the fiber is Succeed or Failed
   * Identify the correct types.
   */
  val await1: UIO[Exit[Nothing, Int]] = Fiber.succeed[Nothing, Int](1).await
  val await2 = Fiber.fromEffect(IO.succeed("run forever").forever)
  val await3: UIO[Exit[Int, Nothing]] = Fiber.fail[Int](1).await
  val await4: UIO[Exit[Exception, Nothing]] = Fiber.fail[Exception](new Exception("error!")).await

  for {
    e1 <- await1
    f2 <- await2
    e2 <- f2.await
    e3 <- await3
    e4 <- await4
  } yield (e1, e2, e3, e4) match {
    case (Exit.Success(o1), Exit.Success(_), Exit.Success(_), Exit.Success(_) ) => o1
    case _ => Exit.fail(new RuntimeException)
  }

  /**
   *   Using `poll` observe if the Fiber is terminated
   *   Identify the correct types.
   */
  val observe1: Fiber[Nothing, Int] = Fiber.succeed[Nothing, Int](1)
  val observe2: IO[Nothing, Fiber[Nothing, Nothing]] = Fiber.fromEffect(IO.succeed("run forever").forever)
  val observe3: Fiber[Int, Nothing] = Fiber.fail[Int](1)
  val observe4: Fiber[Exception, Nothing] = Fiber.fail[Exception](new Exception("error!"))

  def allFibersDone: ZIO[Any,
                         Nothing,
                         (Option[Exit[Nothing, Int]],
                          Option[Exit[Nothing, Nothing]],
                          Option[Exit[Int, Nothing]],
                          Option[Exit[Exception, Nothing]])] = for {
    o1 <- observe1.poll
    o2F <- observe2
    o2 <- o2F.poll
    o3 <- observe3.poll
    o4 <- observe4.poll
  } yield (o1, o2, o3, o4)

  /**
   * Using `flatMap` and `interrupt` to interrupt the fiber `putStrLForeverF`
   * Identify the correct types
   */
  val interruptedF: ZIO[Console, Nothing, Exit[Nothing, Unit]] = for {
    fiber <- putStrLForeverF
    o     <- fiber.interrupt
  } yield o

  val interruptOnEnter: ZIO[Console, IOException, Unit] = for {
    outputFiber <- putStrLForeverF
    inputFiber  <- console.getStrLn.fork
    _           <- inputFiber.join
    _           <- outputFiber.interrupt
  } yield ()

  /**
   * Write a program that asks 2 users for their name and greets them concurrently,
   * At the end join the Fibers and return the result.
   * Use for-comprehension or `flatMap` and `map`.
   *  Identify the correct types
   */
  val sayHello: ZIO[Console, IOException, Unit]     = for {
    _  <- console.putStr("Hello, what's your name(s)?")
    s1 <- console.getStrLn
    _  <- console.putStr(s"hello $s1")
  } yield ()

  val sayHelloBoth: ZIO[Console, IOException, Unit] = for {
    h1 <- sayHello.fork
    h2 <- sayHello.fork
    _ <- h1.join
    _ <- h2.join
  } yield ()

  /**
   * Write a program that computes the sum of a given List of Int and
   * checks if all elements are positive concurrently
   * At the end join the Fibers and return a tuple of their results using `zip`
   */
  def sumPositive(as: UIO[List[Int]]): UIO[(Int, Boolean)] = {
    def check2(f1: Fiber[Nothing, (Int, Boolean)], f2: Fiber[Nothing, (Int, Boolean)]): Fiber[Nothing, (Int, Boolean)] =
      f1.zipWith(f2){ case ((x1, b1), (x2, b2)) =>  (x1 + x2, b1 && b2)}

    as.map { xs =>
      xs.map(x => Fiber.succeed((x, x > 0))).foldRight(Fiber.succeed((0, true)))(check2)
    }.flatMap(_.join)

  }

  /**
   * Using `zipWith`. Write a program that start 2 Fibers with
   * pure values of type int and compute their sum
   * At the end join the Fibers and return a the result
   * Identify the correct return type.
   */
  def sum(a: UIO[Int], b: UIO[Int]): UIO[Int] = for {
    aa <- a.fork
    bb <- b.fork
    res <- aa.zipWith(bb)(_ + _).join
  } yield res

  /**
   * Create a FiberLocal
   */
  val local: UIO[FiberLocal[Int]] = FiberLocal.make

  /**
   * set a value 42 to `local` using `flatMap` and `LocalFiber#set`
   * and then call `FiberLocal#get`
   */
  val updateLocal: UIO[Option[Int]] = for {
    fl <- local
    _  <- fl.set(42)
    newValue <- fl.get
  } yield newValue

  /**
   * Using `locally`. Create FiberLocal that automatically sets
   * a value "Hello" and frees data and return that value
   */
  val locally: UIO[Option[String]] = for {
    fl <- FiberLocal.make[String]
    _  <- fl.locally("Hello")(UIO.succeed(()))
    res <- fl.get
  } yield res

  /**
   * Write a program that executes 2 tasks in parallel
   * combining their results with the specified in a tuple. If
   * either side fails, then the other side will be interrupted.
   */
  def par[A, B](io1: Task[A], io2: Task[B]): Task[(A, B)] = for {
    a <- io1.fork
    b <- io2.fork
    ab <- a.zip(b).join
  } yield ab
}

object zio_parallelism {

  /**
   * Using `zipPar` Write a program that finds the first and the last user in parallel
   * that satisfies a given condition for a given list of users
   * Identify the correct type.
   */
  case class User(id: Int, name: String, subscription: LocalDate)

  def findFirstAndLast(as: List[User])(p: LocalDate => Boolean): UIO[(Option[User], Option[User])] = {
    val findFirst: UIO[Option[User]] = Task.effectTotal(as.find(user => p(user.subscription)))
    val findLast: UIO[Option[User]]  = Task.effectTotal(as.reverse.find(user => p(user.subscription)))
    findFirst zipPar findLast
  }

  /**
   * Using `ZIO.collectAllPar`. Write a program that get users with specified ids in parallel.
   * Identify the correct type.
   */
  val users: Map[Int, User] = Map(
    1 -> User(1, "u1", LocalDate.of(2018, 9, 22)),
    2 -> User(2, "u2", LocalDate.of(2018, 8, 6))
  )

  def getUser(id: Int): IO[String, Either[String, User]] = IO.effect(users(id)).mapError(_.getMessage).either

  def allUser(ids: List[Int]): IO[String, List[Either[String, User]]] = ZIO.collectAllPar(ids.map(getUser))

  /**
   * Using `ZIO.foreachPar`. Write a program that displays the information of users (using `console.putStrLn`)
   * in parallel.
   * Identify the correct ZIO type.
   */
  def printAll(users: List[User]): ZIO[Console, Nothing, List[Unit]] = ZIO.foreachPar(users)(user => console.putStr(user.toString))

  def fib(n: Int): UIO[BigInt] =
    if (n <= 1) UIO.succeed(BigInt(n))
    else fib(n - 1).zipWith(fib(n - 2))(_ + _)

  /**
   * Compute the first 20 fibonacci numbers in parallel.
   */
  val firstTwentyFibs: UIO[List[BigInt]] = UIO.foreachPar((1 to 20).toList)(fib)

  /**
   * Using `ZIO.foreachPar`. Write a program that compute the sum of action1, action2 and action3
   */
  val action1: ZIO[Clock, Nothing, Int] = IO.succeed(1).delay(10.seconds)
  val action2: ZIO[Clock, Nothing, Int] = IO.succeed(2).delay(1.second)
  val action3: ZIO[Clock, Nothing, Int] = IO.succeed(2).delay(1.second)
  val sum: ZIO[Clock, Nothing, Int]     = ZIO.foreachPar(List(action1, action2, action3))(identity).map(_.sum)

  /**
   * Rewrite `printAll` specifying the number of fibers
   * Identify the correct ZIO type.
   */
  def printAll_(users: List[IO[String, List[User]]]): ??? = ???

  /**
   * Using `ZIO#race`. Race queries against primary and secondary databases
   * to return whichever one succeeds first.
   */
  sealed trait Database
  object Database {
    case object Primary   extends Database
    case object Secondary extends Database
  }
  def getUserById(userId: Int, db: Database): Task[User] = ???
  def getUserById(userId: Int): Task[User] = getUserById(userId, Database.Primary).race(getUserById(userId, Database.Secondary))

  /**
   * Using `raceAttempt` Race `leftContestent1` and `rightContestent1` to see
   * which one finishes first and returns Left if the left action will be the winner
   * otherwise it returns the successful value in Right
   */
  val raced2: ??? = ???

  /**
   * Using `raceWith`. Race `a` and `b` and print out the winner's result
   */
  val a: UIO[Int]                                  = UIO.succeedLazy((1 to 1000).sum)
  val b: UIO[Int]                                  = UIO.succeedLazy((1 to 10).sum)
  val firstCompleted1: ZIO[Console, Nothing, Unit] =
    (a.provideSome[Console](identity) raceWith b.provideSome[Console](identity)) (
      {
        case (Exit.Success(left), rightFiber) => console.putStrLn(s"Left won: $left") <* rightFiber.interrupt
        case _ => UIO.succeed(())
      } ,
      {
        case (Exit.Success(right), leftFiber) => console.putStrLn(s"Right won: $right") <* leftFiber.interrupt
        case _ => UIO.succeed(())
      }
  )

  /**
   * Using `raceAll` return the first completed action.
   */
  val a1: ZIO[Clock, Nothing, Int]             = IO.succeed(1).delay(10.seconds)
  val a2: ZIO[Clock, Nothing, Int]             = IO.succeed(2).delay(1.second)
  val a3: ZIO[Clock, Nothing, Int]             = IO.succeed(2).delay(1.second)
  val firstCompleted: ZIO[Clock, Nothing, Int] = ZIO.raceAll(a1, a2 :: a3 :: Nil)

}

object zio_ref {

  /**
   * Using `Ref.make` constructor, create a `Ref` that is initially `0`.
   */
  val makeZero: UIO[Ref[Int]] = Ref.make(0)

  /**
   * Using `Ref#get` and `Ref#set`, change the
   * value to be 10 greater than its initial value. Return the new value.
   */
  val incrementedBy10: UIO[Int] =
    for {
      ref   <- makeZero
      get   <- ref.get
      _     <- ref.set(get + 10)
      value <- ref.get
    } yield value

  /**
   * Using `Ref#update` to atomically increment the value by 10.
   * Return the new value.
   */
  val atomicallyIncrementedBy10: UIO[Int] =
    for {
      ref   <- makeZero
      value <- ref.update(_ + 10)
    } yield value

  /**
   * Refactor this contentious code to be atomic using `Ref#update`.
   */
  def makeContentious1(n: Int): UIO[Fiber[Nothing, List[Nothing]]] = 
    Ref.make(0).flatMap(ref =>
      IO.forkAll(List.fill(n)(ref.get.flatMap(value =>
        ref.set(value + 10)
      ).forever))
    )
  def makeContentious2(n: Int): UIO[Fiber[Nothing, List[Nothing]]] =
    Ref.make(0).flatMap(ref =>
      IO.forkAll(List.fill(n)(ref.update(_ + 10))).forever
    )
  /**
   * Using the `Ref#modify` to atomically increment the value by 10,
   * but return the old value, converted to a string.
   */
  val atomicallyIncrementedBy10PlusGet: UIO[String] =
    for {
      ref   <- makeZero
      value <- ref.modify(v => (v.toString, v + 10))
    } yield value

  /**
   * Using `Ref#updateSome` change the state of a given ref to Active
   * only if the state is Closed
   */
  trait State
  case object Active extends State
  case object Closed extends State

  def setActive(ref: Ref[State], boolean: Boolean): UIO[State] =
    ref.updateSome {
      case Closed => Active
    }

  /**
   * Using `Ref#modifySome` change the state to Closed only if the state was Active and return true
   * if the state is already closed return false
   */
  def setClosed(ref: Ref[State], boolean: Boolean): UIO[Boolean] =
    ref.modifySome(false) {
      case Active => (true, Closed)
    }

  /**
   * RefM allows effects in atomic operations
   */
  /**
   * Create a RefM using `RefM.apply`
   */
  val refM: UIO[RefM[Int]] = RefM.make(4)

  /**
   * update the refM with the square of the old state and print it out in the Console
   */
  val square = for {
      ref <- refM
      v   <- ref.update(i => UIO.succeed(i * i) <* UIO.effectTotal(println(s"$i")) )
    } yield v

  val t =  for {
     ref <- RefM.make(2)
     v   <- ref.update(n => console.putStrLn("Hello World!") *> IO.succeed(3))
     _   <- putStrLn("Value = " + v) // Value = 5
   } yield ()
}

object zio_promise {


  val t: ZIO[Console with Clock, Nothing, Int] = for {
    promise <- Promise.make[Nothing, Int]
    _       <- promise.succeed(42).delay(5.second).fork
    value   <- promise.await // Resumes when forked fiber completes promise
    _       <- console.putStr(s"you got your stuff buddy, $value")
  } yield value

  /**
   * Using `Promise.make` construct a promise that cannot
   * fail but can be completed with an integer.
   */
  val makeIntPromise: UIO[Promise[Nothing, Int]] = Promise.make[Nothing, Int]

  /**
   * Using `Promise.succeed`, complete a `makeIntPromise` with an integer 42
   */
  val completed1: UIO[Boolean] =
    for {
      promise   <- makeIntPromise
      completed <- promise.succeed(42)
    } yield completed

  /**
   * Using `Promise.fail`, try to complete `makeIntPromise`.
   * Explain your findings
   * We can't fail an "unfailable" promise as we can't construct anything of type `Nothing`
   */
  val errored1: UIO[Boolean] =
    for {
      promise   <- makeIntPromise
      completed <- (promise ? : UIO[Boolean])
    } yield completed

  /**
   * Create a new promise that can fail with a `Error` or produce a value of type `String`
   */
  case class Error(msg: String)
  val errored2: UIO[Boolean] =
    for {
      promise   <- Promise.make[Error, String]
      completed <- promise.fail(Error("boom!"))
    } yield completed

  /**
   * Make a promise that might fail with `Error`or produce a value of type
   * `Int` and interrupt it using `interrupt`.
   */
  val interrupted: UIO[Boolean] =
    for {
      promise   <- Promise.make[Error, String]
      completed <- promise.interrupt
    } yield completed

  /**
   * Using `await` retrieve a value computed from inside another fiber
   */
  val handoff1: ZIO[Console with Clock, Nothing, Int] =
    for {
      promise <- Promise.make[Nothing, Int]
      _       <- (clock.sleep(10.seconds) *> promise.succeed(42)).fork //this fiber will succeed the promise
      _       <- putStrLn("Waiting for promise to be completed...")
      value   <- promise.await
      _       <- putStrLn("Got: " + value)
    } yield value

  val handoff10: ZIO[Console with Clock, Nothing, Int] =
    for {
      promise <- Promise.make[Nothing, Int]
      _       <- promise.succeed(42).delay(10.seconds).fork //this fiber will succeed the promise
      _       <- putStrLn("Waiting for promise to be completed...")
      value   <- promise.await
      _       <- putStrLn("Got: " + value)
    } yield value

  /**
   * Using `await`. retrieve a value from a promise that was failed in another fiber.
   */
  val handoff2: ZIO[Console with Clock, Error, Int] =
    for {
      promise <- Promise.make[Error, Int]
      _       <- (clock.sleep(10.seconds) *> promise.fail(new Error("Uh oh!"))).fork //this fiber will fail the promise
      _       <- putStrLn("Waiting for promise to be completed...")
      value   <- promise.await
      _       <- putStrLn("This line will NEVER be executed")
    } yield value

  val handoff20: ZIO[Console with Clock, Error, Int] =
    for {
      promise <- Promise.make[Error, Int]
      _       <- promise.fail(new Error("Uh oh!")).delay(10.seconds).fork //this fiber will fail the promise
      _       <- putStrLn("Waiting for promise to be completed...")
      value   <- promise.await
      _       <- putStrLn("This line will NEVER be executed")
    } yield value


  /**
   * Using `await`. Try to retrieve a value from a promise
   * that was interrupted in another fiber.
   */
  val handoff3: ZIO[Clock with Console, Nothing, Int] =
    for {
      promise <- Promise.make[Nothing, Int]
      _       <- promise.interrupt.delay(10.seconds).fork // this fiber will interrupt the promise
      value   <- promise.await
      _       <- putStrLn("This line will NEVER be executed")
    } yield value

  /**
   * Build auto-refreshing cache using `Ref`and `Promise`
   */
  class Cache[K, V](ref: Ref[Map[K, V]], expireIn: Duration, refreshAfter: Duration) {

    def get(k: K): UIO[Option[V]] = for {
      map <- ref.get
    } yield map.get(k)

    def put(k: K, v: V)(refreshAction: K => UIO[V]): ZIO[Clock, Nothing, Unit] =
      for {
        map <- ref.update(_.updated(k, v)).provideSome[Clock](identity)
        // we could use a promise that gets filled by this fiber, and interrupt it when the next put occurs. Problem: where to store this promise? In the map?
        newVFiber <- refreshAction(k).delay(refreshAfter).fork
        _         <- newVFiber.join.flatMap(newVal => ref.update(_.updated(k, newVal))).fork
      } yield ()
  }

}

object zio_queue {

  /**
   * Using `Queue.bounded`, create a queue for `Int` values with a capacity of 10
   */
  val makeQueue: UIO[Queue[Int]] = Queue.bounded[Int](10)

  /**
   * Place `42` into the queue using `Queue#offer`.
   */
  val offered1: UIO[Unit] =
    for {
      queue <- makeQueue
      _     <- queue.offer(42)
    } yield ()

  /**
   * Using `Queue#take` take an integer value from the queue
   */
  val taken1: UIO[Int] =
    for {
      queue <- makeQueue
      _     <- queue.offer(42)
      value <- queue.take
    } yield value

  /**
   * In a child fiber, place 2 values into a queue, and in the main fiber, read
   *  2 values from the queue.
   */
  val offeredTaken1: UIO[(Int, Int)] =
    for {
      queue <- makeQueue
      _     <- (queue.offer(42) *> queue.offer(43)).fork
      v1    <- queue.take
      v2    <- queue.take
    } yield (v1, v2)

  val offeredTaken1AndPrint: ZIO[Console with Clock, Nothing, (Int, Int)] =
    for {
      queue <- makeQueue
      _     <- (queue.offer(42).delay(3.seconds) *> queue.offer(43).delay(4.seconds)).fork
      v1    <- queue.take
      _     <- console.putStrLn(s"fetched $v1 from the queue")
      v2    <- queue.take
      _     <- console.putStrLn(s"fetched $v2 from the queue")
    } yield (v1, v2)

  /**
   * In a child fiber, read infintely many values out of the queue and write
   * them to the console. In the main fiber, write 100 values into the queue,
   * using `ZIO.foreach` on a `List`.
   */
  val infiniteReader1: ZIO[Console, Nothing, List[Boolean]] =
    for {
      queue <- Queue.bounded[String](10)
      _     <- queue.take.flatMap(console.putStrLn).forever.fork
      vs    <- ZIO.foreach((1 to 100).toList)(n => queue.offer(s"element $n"))
    } yield vs

  /**
   * Using  `Queue`, `Ref`, and `Promise`, implement an "actor" like construct
   * that can atomically update the values of a counter.
   */
  sealed trait Message
  case class Increment(amount: Int) extends Message

  val makeCounter: UIO[Message => UIO[Int]] =
    for {
      counter <- Ref.make(0)
      mailbox <- Queue.bounded[(Message, Promise[Nothing, Int])](100)
       //define the loop
      _       <- (
                    for {
                      mp <- mailbox.take
                      (m, p) = mp
                      _ <- m match {
                        case  Increment(n) =>
                          counter.modify(current => (current + n, current + n)).flatMap(current => p.succeed(current))
                      }
                    } yield ()
                  ).forever.fork
    } yield { (message: Message) =>
      for {
        p   <- Promise.make[Nothing, Int]
        _   <- mailbox.offer((message, p))
        res <- p.await
      } yield res
    }

  // generic actor
  case class Actor[-A, +E, +B](run: A => IO[E, B]) {
    def !(a: A): IO[E, B] = run(a)
  }

  object Actor {
    def makeActor[S, E, A, B](s: S)(receive: (S, A) => IO[E, (S, B)]): UIO[Actor[A, E, B]] =
    for {
      state   <- Ref.make(s)
      mailbox <- Queue.bounded[(A, Promise[E, B])](100)
      _ <- { // processing of the actor, must run in a separate fiber and loop through the mailbox and perform the processing
        for {
          elem         <- mailbox.take
          s            <- state.get
          (a, promise) = elem
          out          <- receive(s, a)
          (newS, b)    = out
          _            <- state.set(newS) *> promise.succeed(b)
        } yield ()
      }.forever.fork
    } yield
      Actor[A, E, B] { (a: A) => for { //enqueue input message and a promise for the output message
          p <- Promise.make[E, B]
          _ <- mailbox.offer((a, p))
          b <- p.await
        } yield b
      }
  }

  val counterExample: UIO[Int] =
    for {
      counter <- makeCounter
      _       <- IO.collectAllPar(List.fill(100)(IO.foreach((0 to 100).map(Increment(_)))(counter)))
      value   <- counter(Increment(0))
    } yield value


  /**
   * using `Queue.sliding` create a queue with capacity 3 using sliding strategy
   */
  val slidingQ: UIO[Queue[Int]] = Queue.sliding(3)

  /**
   * Using `Queue#offerAll`, offer 4 integer values to a sliding queue with capacity of 3
   * and take them all using `Queue#takeAll`. What will you get as result? 3, 4, 5
   */
  val offer4TakeAllS: UIO[List[Int]] = for {
    queue  <- Queue.sliding[Int](3)
    _      <- queue.offerAll(List(1, 2, 3, 4, 5))
    values <- queue.takeAll
  } yield values

  /**
   * using `Queue.dropping` create a queue with capacity 3 using sliding strategy
   */
  val dropingQ: UIO[Queue[Int]] = Queue.dropping(3)

  /**
   * Using `Queue#offerAll`, offer 4 integer values to a dropping queue with capacity of 3
   * and take them all using `Queue#takeAll`. What will you get as result? 1, 2, 3
   */
  val offer4TakeAllD: UIO[List[Int]] = for {
    queue  <- Queue.dropping[Int](3)
    _      <- queue.offerAll(List(1, 2, 3, 4, 5))
    values <- queue.takeAll
  } yield values

}

object zio_semaphore {

  /**
   *Using `Semaphore.make`, create a semaphore with 1 permits.
   */
  val semaphore: UIO[Semaphore] = ???

  /**
   * Using `Semaphore#acquire` acquire permits sequentially (using IO.???) and
   * return the number of available permits using `Semaphore#available`.
   */
  val nbAvailable1: UIO[Long] =
    for {
      semaphore <- Semaphore.make(5)
      _         <- (??? : UIO[Unit])
      available <- (??? : UIO[Long])
    } yield available

  /**
   * Using `Semaphore#acquireN` acquire permits in parallel (using IO.???) and
   * return the number of available permits.
   */
  val nbAvailable2: UIO[Long] =
    for {
      semaphore <- Semaphore.make(5)
      _         <- (??? : UIO[Unit])
      available <- (??? : UIO[Long])
    } yield available

  /**
   * Acquire one permit and release it using `Semaphore#release`.
   * How much permit are available?
   */
  val nbAvailable3: UIO[Long] =
    for {
      semaphore <- Semaphore.make(5)
      _         <- (??? : UIO[Unit])
      _         <- (??? : UIO[Unit])
      available <- (??? : UIO[Long])
    } yield available

  /**
   * Using `Semaphore#withPermit` prepare a semaphore that once it acquires a permit
   * putStrL("is completed")
   */
  val s: ZIO[Clock, Nothing, Unit] =
    for {
      semaphore <- Semaphore.make(1)
      p         <- Promise.make[Nothing, Unit]
      _         <- (??? : UIO[Unit])
      _         <- semaphore.acquire.delay(1.second).fork
      msg       <- p.await
    } yield msg

  /**
   * Implement `createAcceptor` to create a connection acceptor that will
   * accept at most the specified number of connections.
   */
  trait Request
  trait Response
  type Handler = Request => UIO[Response]
  lazy val defaultHandler: Handler                   = ???
  def startWebServer(handler: Handler): UIO[Nothing] =
    // Pretend this is implemented.
    ???
  def limitedHandler(limit: Int, handler: Handler): UIO[Handler] =
    ???
  val webServer1k: UIO[Nothing] =
    for {
      acceptor <- limitedHandler(1000, defaultHandler)
      value    <- startWebServer(acceptor)
    } yield value
}

object zio_stream {
  import scalaz.zio.stream.Stream

  /**
   * Create a stream using `Stream.apply`
   */
  val streamStr: Stream[Nothing, Int] = ???

  /**
   * Create a stream using `Stream.fromIterable`
   */
  val stream1: Stream[Nothing, Int] = (1 to 42) ?

  /**
   * Create a stream using `Stream.fromChunk`
   */
  val chunk: Chunk[Int]                  = Chunk(43 to 100: _*)
  val stream2: Stream[Nothing, Int] = ???

  /**
   * Make a queue and use it to create a stream using `Stream.fromQueue`
   */
  val stream3: UIO[Stream[Nothing, Int]] = ???

  /**
   * Create a stream from an effect producing a String
   * using `Stream.lift`
   */
  val stream4: Stream[Nothing, String] = ???

  /**
   * Create a stream of ints that starts from 0 until 42,
   * using `Stream#unfold`
   */
  val stream5: Stream[Nothing, Int] = ???

  /**
   * Using `Stream.unfoldM`, create a stream of lines of input from the user,
   * terminating when the user enters the command "exit" or "quit".
   */
  import java.io.IOException
  import scalaz.zio.console.getStrLn
  val stream6: ZStream[Console, IOException, String] = ???

  /**
   * Using `withEffect` log every element.
   */
  val loggedInts: ZStream[Console, Nothing, Int] = stream1 ?

  /**
   * Using `Stream#filter` filter the even numbers
   */
  val evenNumbrers: Stream[Nothing, Int] = stream1 ?

  /**
   * Using `Stream#takeWhile` take the numbers that are less than 10
   */
  val lessThan10: Stream[Nothing, Int] = stream1 ?

  /**
   * Print out each value in the stream using `Stream#foreach`
   */
  val printAll: ZIO[Console, Nothing, Unit] = stream1 ?

  /**
   * Convert every Int into String using `Stream#map`
   */
  val toStr: Stream[Nothing, String] = stream1 ?

  /**
   * Merge two streams together using `Stream#merge`
   */
  val mergeBoth: Stream[Nothing, Int] = (stream1, stream2) ?

  /**
   * Create a Sink using `Sink#readWhile` that takes an input of type String and check if it's not empty
   */
  val sink: Sink[Nothing, String, String, List[String]] = ???

  /**
   * Run `sink` on the stream to get a list of non empty string
   */
  val stream = Stream("Hello", "Hi", "Bonjour", "cześć", "", "Hallo", "Hola")
  val firstNonEmpty: UIO[List[String]] = ???

}

object zio_schedule {

  /**
   * Using `Schedule.recurs`, create a schedule that recurs 5 times.
   */
  val fiveTimes: Schedule[Any, Int] = ???

  /**
   * Using the `ZIO.repeat`, repeat printing "Hello World"
   * five times to the console.
   */
  val repeated1 = putStrLn("Hello World") ?

  /**
   * Using `Schedule.spaced`, create a schedule that recurs forever every 1 second
   */
  val everySecond: Schedule[Any, Int] = ???

  /**
   * Using the `&&` method of the `Schedule` object, the `fiveTimes` schedule,
   * and the `everySecond` schedule, create a schedule that repeats fives times,
   * evey second.
   */
  val fiveTimesEverySecond = ???

  /**
   *  Using the `ZIO#repeat`, repeat the action
   *  putStrLn("Hi hi") using `fiveTimesEverySecond`.
   */
  val repeated2 = putStrLn("Hi hi") ?

  /**
   * Using `Schedule#andThen` the `fiveTimes`
   * schedule, and the `everySecond` schedule, create a schedule that repeats
   * fives times rapidly, and then repeats every second forever.
   */
  val fiveTimesThenEverySecond = ???

  /**
   * Using `ZIO#retry`, retry the following error
   * a total of five times.
   */
  val error1   = IO.fail("Uh oh!")
  val retried5 = error1 ?

  /**
   * Using the `Schedule#||`, the `fiveTimes` schedule,
   * and the `everySecond` schedule, create a schedule that repeats the minimum
   * of five times and every second.
   */
  val fiveTimesOrEverySecond = ???

  /**
   * Using `Schedule.exponential`, create an exponential schedule that starts from 10 milliseconds.
   */
  val exponentialSchedule: Schedule[Any, Duration] =
    ???

  /**
   * Using `Schedule.jittered` produced a jittered version of
   * `exponentialSchedule`.
   */
  val jitteredExponential = exponentialSchedule ?

  /**
   * Using `Schedule.whileOutput`, produce a filtered schedule from
   * `Schedule.forever` that will halt when the number of recurrences exceeds 100.
   */
  val oneHundred = Schedule.forever.whileOutput(???)

  /**
   * Using `Schedule.identity`, produce a schedule that recurs forever,
   * without delay, returning its inputs.
   */
  def inputs[A]: Schedule[A, A] = ???

  /**
   * Using `Schedule#collect`, produce a schedule that recurs
   * forever, collecting its inputs into a list.
   */
  def collectedInputs[A]: Schedule[A, List[A]] =
    Schedule.identity[A] ?

  /**
   * Using  `*>`, combine `fiveTimes` and `everySecond` but return the output of `everySecond`.
   */
  val fiveTimesEverySecondR: Schedule[Any, Int] = ???

  /**
   * Produce a jittered schedule that first does exponential spacing (starting
   * from 10 milliseconds), but then after the spacing reaches 60 seconds,
   * switches over to fixed spacing of 60 seconds between recurrences, but will
   * only do that for up to 100 times, and produce a list of the inputs to
   * the schedule.
   */
  import scalaz.zio.random.Random
  def mySchedule[A]: ZSchedule[Clock with Random, A, List[A]] =
    ???
}
