package com.evolet.tachyon

import android.app.Application
import com.evolet.tachyon.agents.AgentBus
import com.evolet.tachyon.agents.Perceiver
import com.evolet.tachyon.agents.Planner
import com.evolet.tachyon.agents.Recoverer
import com.evolet.tachyon.agents.Verifier
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
    val bus = AgentBus()

    // ERAYA agents (INTEGRATIONSv2.md §3)
    private val extraction = ExtractionAgent(prompts)
    val perceiver = Perceiver(bus, engines::asr)
    val planner = Planner(bus, engines::llm, extraction::extract, Verifier())
    val recoverer = Recoverer(bus, engines::retry).also { it.start(scope) }

    val confirmation = ConfirmationLoop(db.commitmentDao(), bus)
    val session = SessionController(app, scope, engines, perceiver, planner, db, settings)
}
