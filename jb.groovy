#!/usr/bin/env groovy
import com.brandon.jenkins.cmn.*

def call() {
    def commonStgs = new com.brandon.jenkins.cmn.CommonStages(this)
    def commonAuto = new com.brandon.jenkins.auto.AutomationJboss(this)

    node(GlobVars.master) {
        cleanWs() // eliminar workspace si existe

        try {
            // 1️⃣ Cargar CSV desde resources
            env.cvsContent = libraryResource('data/_Jboss_.csv')

            // 2️⃣ Inicializar entorno
            commonStgs.stgInitialize()

            // 3️⃣ Leer la data (tu método ya lo hace)
            def servidoresPorApp = commonAuto.stgReadData()

            // 4️⃣ Declarar parámetros de entrada en GUI
            properties([
                parameters([
                    choice(name: 'APP', choices: servidoresPorApp*.app.unique().join('\n'), description: 'Selecciona la App'),
                    choice(name: 'APLICATIVO', choices: servidoresPorApp*.aplicativo.unique().join('\n'), description: 'Selecciona el Aplicativo')
                ])
            ])

            // 5️⃣ Ejecutar el 
            commonAuto.stgCreateUser(servidoresPorApp, params.APP, params.APLICATIVO)

        } catch (e) {
            commonStgs.printOutput("Se presentó un error: ${e}", "Y")
        }

        postBuildActions()
        cleanWs()
    }

    return this
}

def csv = new File("${WORKSPACE}/data/my_jboss.csv")
def lines = csv.readLines().drop(1)
def data = lines.collect { line ->
    def (hostname, ip, aplicativo, ambiente, app, user) = line.split(";")
    return [app: app.trim(), aplicativo: aplicativo.trim()]
}
return data*.app.unique().sort()
