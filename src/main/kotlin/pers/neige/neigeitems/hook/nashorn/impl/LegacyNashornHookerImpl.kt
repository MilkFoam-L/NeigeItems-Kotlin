package pers.neige.neigeitems.hook.nashorn.impl

import jdk.nashorn.api.scripting.NashornScriptEngine
import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import jdk.nashorn.api.scripting.ScriptObjectMirror
import pers.neige.neigeitems.hook.nashorn.NashornHooker
import java.io.Reader
import javax.script.CompiledScript
import javax.script.Invocable
import javax.script.ScriptEngine

class LegacyNashornHookerImpl : NashornHooker() {
    private val engineFactory = NashornScriptEngineFactory()

    override fun getNashornEngine(): ScriptEngine {
        return engineFactory.getScriptEngine(arrayOf("-Dnashorn.args=--language=es6"), this::class.java.classLoader)
    }

    override fun compile(string: String): CompiledScript {
        return (engineFactory.getScriptEngine(arrayOf("-Dnashorn.args=--language=es6"), this::class.java.classLoader) as NashornScriptEngine).compile(string)
    }

    override fun compile(reader: Reader): CompiledScript {
        return (engineFactory.getScriptEngine(arrayOf("-Dnashorn.args=--language=es6"), this::class.java.classLoader) as NashornScriptEngine).compile(reader)
    }

    override fun invoke(compiledScript: pers.neige.neigeitems.script.CompiledScript, function: String, map: HashMap<String, Any>?, vararg args: Any): Any? {
        val newObject: ScriptObjectMirror = (compiledScript.scriptEngine as Invocable).invokeFunction("newObject") as ScriptObjectMirror
        map?.forEach { (key, value) -> newObject[key] = value }
        return newObject.callMember(function, *args)
    }
}