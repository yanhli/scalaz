package scalaz
package effect

trait MonadCatchIO[M[_]] extends MonadIO[M] {
  /** Executes the handler if an exception is raised. */
  def except[A](ma: M[A])(handler: Throwable ⇒ M[A]): M[A]
}     

object MonadCatchIO extends MonadCatchIOFunctions {
  @inline def apply[M[_]](implicit M: MonadCatchIO[M]): MonadCatchIO[M] = M
}

sealed trait MonadCatchIOFunctions {
  def except[M[_], A](ma: M[A])(handler: Throwable ⇒ M[A])(implicit M: MonadCatchIO[M]): M[A] =
    M.except(ma)(handler)
          
  import scalaz.syntax.monad._

 /**
   * Executes the handler for exceptions that are raised and match the given predicate.
   * Other exceptions are rethrown.
   */
  def catchSome[M[_]: MonadCatchIO, A, B](ma: M[A])(p: Throwable => Option[B], handler: B => M[A]): M[A] =
    except(ma)(e => p(e) match {
      case Some(z) => handler(z)
      case None => throw e
    })

  /**
   * Returns a disjunction result which is right if no exception was raised, or left if an
   * exception was raised.
   */
  def catchLeft[M[_]: MonadCatchIO, A](ma: M[A]): M[Throwable \/ A] =
    except(ma.map(\/.right[Throwable, A]))(t => \/.left[Throwable, A](t).point[M])

  /** Like "catchLeft" but takes a predicate to select which exceptions are caught. */
  def catchSomeLeft[M[_]: MonadCatchIO, A, B](ma: M[A])(p: Throwable => Option[B]): M[B \/ A] =
    catchLeft(ma) map (_.leftMap(e => p(e).getOrElse(throw e)))

  /**Like "finally", but only performs the final action if there was an exception. */
  def onException[M[_]: MonadCatchIO, A, B](ma: M[A], action: M[B]): M[A] = 
    except(ma)(e ⇒
      for {
        _ ← action
        a ← (throw e): M[A]
      } yield a)
                 
  def bracket[M[_]: MonadCatchIO, A, B, C](before: M[A])(after: A ⇒ M[B])(during: A ⇒ M[C]): M[C] =
    for {
      a ← before
      r ← onException(during(a), after(a))
      _ ← after(a)
    } yield r

  /**Like "bracket", but takes only a computation to run afterward. Generalizes "finally". */
  def ensuring[M[_]: MonadCatchIO, A, B](ma: M[A], sequel: M[B]): M[A] = 
    for {
      r ← onException(ma, sequel)
      _ ← sequel
    } yield r

  /**A variant of "bracket" where the return value of this computation is not needed. */
  def bracket_[M[_]: MonadCatchIO, A, B, C](before: M[A])(after: M[B])(during: M[C]): M[C] =
    bracket(before)(_ ⇒ after)(_ ⇒ during)
                             
  /**A variant of "bracket" that performs the final action only if there was an error. */
  def bracketOnError[M[_]: MonadCatchIO, A, B, C](before: M[A])(after: A ⇒ M[B])(during: A ⇒ M[C]): M[C] = 
    for {
      a ← before
      r ← onException(during(a), after(a))
    } yield r      

  /** An automatic resource management. */
  def using[M[_], A, B](ma: M[A])(f: A => M[B])(implicit M: MonadCatchIO[M], resource: Resource[A]) =
    bracket(ma)(resource.close(_).liftIO[M])(f)

}

