package apps

import io.jooby.ExecutionMode
import io.jooby.Kooby
import io.jooby.runApp
import kotlinx.coroutines.delay

/** Class version: */
class App : Kooby({


  get { "Hi Kotlin!" }

  get("/suspend") {
    delay(100)
    "Hi Coroutine"
  }

  get("/ctx-access") {
    ctx.pathString()
  }

  get("/ctx-arg") { ctx ->
    ctx.pathString()
  }
})

/** run class: */
fun runClass(args: Array<String>) {
  runApp(::App, args)
}

/** run class with mode: */
fun runWithMode(args: Array<String>) {
  runApp(ExecutionMode.DEFAULT, args, ::App)
}

/** run inline: */
fun runInline(args: Array<String>) {
  runApp(args) {
    get { "Hi Kotlin!" }

    get("/suspend") {
      delay(100)
      "Hi Coroutine"
    }

    get("/ctx-access") {
      ctx.pathString()
    }

    get("/ctx-arg") { ctx ->
      ctx.pathString()
    }
  }
}
