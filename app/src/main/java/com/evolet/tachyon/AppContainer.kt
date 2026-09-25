package com.evolet.tachyon

import android.app.Application
import com.evolet.tachyon.data.AppDb
import com.evolet.tachyon.data.AppSettings
import com.evolet.tachyon.eraya.ConfirmationLoop
import com.evolet.tachyon.eraya.ExtractionAgent
import com.evolet.tachyon.eraya.Prompts
import com.evolet.tachyon.net.Connectivity
import com.evolet.tachyon.session.SessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI (no Hilt). One instance per process, built in TachyonApp.onCreate. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = AppSettings(app)
    val db = AppDb.build(app)
    val connectivity = Connectivity(app)
    val prompts = Prompts(app)

    val engines = EngineRegistry(app, settings, scope)       // starts loading engines immediately
    val confirmation = ConfirmationLoop(db.commitmentDao())
    val session = SessionController(app, scope, engines, ExtractionAgent(prompts), db, settings)
}
