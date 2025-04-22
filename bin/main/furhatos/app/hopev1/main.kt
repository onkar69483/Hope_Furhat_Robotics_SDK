package furhatos.app.hopev1

import furhatos.app.hopev1.flow.Init
import furhatos.flow.kotlin.Flow
import furhatos.skills.Skill

class Hopev1Skill : Skill() {
    override fun start() {
        Flow().run(Init)
    }
}

fun main(args: Array<String>) {
    Skill.main(args)
}
