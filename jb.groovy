


// 1️⃣ prioridad: Config
def configGroups = serverGroups.findAll {
    it.toLowerCase().contains('config')
}

// 2️⃣ prioridad: LB (excluyendo los que ya están en config)
def lbGroups = serverGroups.findAll {
    it.toLowerCase().contains('lb') &&
    !it.toLowerCase().contains('config')
}

// 3️⃣ resto
def otherGroups = serverGroups.findAll {
    !it.toLowerCase().contains('config') &&
    !it.toLowerCase().contains('lb')
}

// 🔥 orden final
def orderedServerGroups = configGroups + lbGroups + otherGroups

commonStgs.printOutput(
    "Orden de inicio server-groups: ${orderedServerGroups}",
    "G"
)

























// Normalizar nombres
def serverGroups = (getServerGroups =~ serverGroupPattern)
        .collect { it[1] }
        .drop(3)

// 🔑 separar por prioridad
def configGroups = serverGroups.findAll {
    it.toLowerCase().contains('config')
}

def otherGroups = serverGroups.findAll {
    !it.toLowerCase().contains('config')
}

// 🔥 orden final
def orderedServerGroups = configGroups + otherGroups

commonStgs.printOutput(
    "Orden de inicio server-groups: ${orderedServerGroups}",
    "G"
)















/host=master:read-children-resources(child-type=server, include-runtime=true)
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



void stgCreateUser(def servidoresPorApp, String appSeleccionado, String aplicativoSeleccionado) {
    command.stage("Subir servicio EMS") {
        try {
            servidoresPorApp.each { app ->
                if (app.app.trim().equalsIgnoreCase(appSeleccionado.trim()) &&
                    app.aplicativo.trim().equalsIgnoreCase(aplicativoSeleccionado.trim())) {

                    command.echo "✅ Coincidencia encontrada para App=${app.app}, Aplicativo=${app.aplicativo}"

                    command.node(app.hostname) {
                        command.sh """
                            sudo -u ${app.user} bash -c '
                                echo "Coincidencia encontrada con ${app.app} / ${app.aplicativo}"
                                echo "Termino"
                            '
                        """
                    }
                }
            }
        } catch (e) {
            commonStgs.printOutput("${e}", "R")
        }
    }
}






import groovy.json.JsonSlurper

def hosts = []

// --- PARSEO AISLADO ---
def parsed = new JsonSlurper().parseText(getHostsCommand)

// copiar SOLO lo necesario a estructuras serializables
def hostConnectionsTmp = parsed
        ?.result
        ?.'core-service'
        ?.'management'
        ?.'host-connection'

// romper referencias CPS
parsed = null

if (hostConnectionsTmp) {
    hostConnectionsTmp.each { k, v ->
        if (k.startsWith('master-')) {
            hosts << k.toString()
        }
    }
}

// romper referencia final
hostConnectionsTmp = null
// --- FIN PARSEO ---

commonStgs.printOutput("Los hosts son: ${hosts}", "G")

command.sleep(120)




{

"outcome" => "failed",
"result" => undefined,
"failure-description" => "WFLYCTL0031: No operation named 'start' exists at address [(\"host\" => \"sla-c01-B0\")]",
"rolled-back" => true
}
[



pipeline {
    agent { label 'linux' }

    stages {
        stage('Prueba de conexión') {
            steps {
                script {

                    def nodos = [
                        "linux",
                        "linux",
                        "linux"
                    ]

                    def exitos = []
                    def fallidos = []

                    nodos.each { nodoIt ->

                        node(nodoIt) {

                            def servidores = params."Lista Servidores"
                            def listaServidores = servidores.split("\n")

                            listaServidores.each { servidor ->

                                def datos = servidor.trim().split("\\s+")
                                def host = datos[0]

                                try {

                                    sh """
                                        nc -zv -w 1 ${host} 22
                                    """

                                    exitos << [
                                        nodo: nodoIt,
                                        servidor: host
                                    ]

                                } catch (e) {

                                    fallidos << [
                                        nodo: nodoIt,
                                        servidor: host
                                    ]
                                }
                            }
                        }
                    }

                    echo "=============================="
                    echo "SERVIDORES CON CONEXIÓN EXITOSA"
                    echo "=============================="

                    exitos.each {
                        echo "Nodo: ${it.nodo} -> Servidor: ${it.servidor}"
                    }

                    echo "=============================="
                    echo "SERVIDORES SIN CONEXIÓN"
                    echo "=============================="

                    fallidos.each {
                        echo "Nodo: ${it.nodo} -> Servidor: ${it.servidor}"
                    }

                    echo "=============================="
                    echo "RESUMEN"
                    echo "=============================="
                    echo "Exitosos: ${exitos.size()}"
                    echo "Fallidos: ${fallidos.size()}"
                }
            }
        }
    }
}





