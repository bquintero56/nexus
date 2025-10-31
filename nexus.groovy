void searchLogsAndUploadToNexus(String server, String paths, String files, String user, String token, String nexusUrl) {
    command.stage("Upload Logs to Nexus")

    if (commonStg.stNodeOnline(server)) {
        try {
            if (!user) {
                command.error("No se encontró usuario para el servidor ${server}.")
            }

            def fileList = files.split(',').collect { it.trim() }

            fileList.each { fileOne ->
                command.echo "Procesando archivo: ${fileOne}"

                // 1️⃣ Validar que el archivo exista
                def checkCmd = """sudo -u ${user} bash -c '[ -f "${paths}/${fileOne}" ] && echo "EXISTE" || echo "NO_EXISTE"'"""
                def checkResult = command.sh(script: checkCmd, returnStdout: true).trim()

                if (checkResult != "EXISTE") {
                    command.echo "⚠️ El archivo ${paths}/${fileOne} no existe, se omite."
                    return
                }

                def uploadCmd = """
                    sudo -u ${user} bash -c '
                        curl -s --insecure -x 11:88 \
                             -u "${token}" \
                             --upload-file "${paths}/${fileOne}" \
                             "${nexusUrl}/${server}/${fileOne}"
                    '
                """
                Archivos subidos a Nexus:<br> ${nexusURL.split(',').collect { "<a href='${it.trim()}' target='_blank'>${it.trim()}</a>" }.join('<br>')}

                command.echo "🚀 Subiendo ${fileOne} a Nexus..."
                command.sh(script: uploadCmd)

                command.echo "✅ Archivo ${fileOne} subido correctamente a: ${nexusUrl}/${server}/${fileOne}"
            }

        } catch (e) {
            commonStg.printOutput("Error al subir logs: ${e.message}", "R")
        }
    } else {
        commonStg.printOutput("El servidor ${server} no está conectado", "Y")
    }
}


if (uploadedUrls) {
    def urlsString = uploadedUrls.collect { f ->
        "Log del archivo <b>${f.file}</b>: <a href='${f.url}' target='_blank'>${f.url}</a>"
    }.join('<br>')
    sentEmail(emailList, urlsString, paths)
    command.echo "📧 Correo enviado con ${uploadedUrls.size()} archivos subidos a Nexus."
}


pipeline {
    agent any
    stages {
        stage('Conexiones SSH') {
            steps {
                script {
                    def r1 = sh(script: 'ssh -p 22 user@host1 "ls /tmp"', returnStatus: true)
                    echo "Resultado host1: ${r1}"

                    def r2 = sh(script: 'ssh -p 22 user@host2 "uptime"', returnStatus: true)
                    echo "Resultado host2: ${r2}"

                    def r3 = sh(script: 'ssh -p 22 user@host3 "whoami"', returnStatus: true)
                    echo "Resultado host3: ${r3}"

                    echo "🟩 Pipeline completado aunque haya errores"
                }
            }
        }
    }
}


void crearComando(def servidoresPorApp, String app, String aplicativo) {
    command.stage("Validar WAR detenidos") {
        try {
            command.currentBuild.displayName = "Aplicación: ${app}"
            command.currentBuild.description = "Validar WAR detenidos para ${app}"

            servidoresPorApp.each { apps ->
                if (apps.app.trim().equalsIgnoreCase(app.trim())) {
                    try {
                        // 1️⃣ Ejecutar comando remoto y capturar salida
                        def salida = command.sh(
                            script: """
                                ssh brandon@${apps.ip} 'sudo -u bra /opt/jboss-eap/bin/jboss-cli.sh -c --commands="deployment-info"'
                            """,
                            returnStdout: true
                        ).trim()

                        // 2️⃣ Mostrar toda la salida del CLI
                        commonStgs.printOutput("📋 Salida completa del comando en ${apps.ip}:", "B")
                        commonStgs.printOutput(salida, "G")

                        // 3️⃣ Procesar solo las líneas con .war y STOPPED
                        def warsDetenidos = []
                        salida.eachLine { linea ->
                            if (linea.contains(".war") && linea.contains("STOPPED")) {
                                def nombreWar = linea.tokenize()[0]  // toma la primera columna (ej: log_api.war)
                                warsDetenidos << nombreWar
                            }
                        }

                        // 4️⃣ Mostrar resultado filtrado
                        if (warsDetenidos) {
                            commonStgs.printOutput("🚫 WARs detenidos encontrados:", "Y")
                            warsDetenidos.each { war ->
                                commonStgs.printOutput(" - ${war}", "Y")
                            }
                        } else {
                            commonStgs.printOutput("✅ No se encontraron WARs detenidos.", "G")
                        }

                    } catch (e) {
                        commonStgs.printOutput("❌ Error al ejecutar en ${apps.ip}: ${e.message}", "R")
                    }
                }
            }

        } catch (e) {
            commonStgs.printOutput("❌ Error general en crearComando: ${e.message}", "R")
        }
    }
}



void stgCreateUserTibco(def usuario, def correo, def roles) {
    command.stage("Create User Tibco") {
        try {
            def projectName = utilsStgs.getNamePipeline()
            def password = passwordCreate.generate(9)

            // Definición de URLs
            def urlDev = "https://dev.echo/teas/task"
            def urlTest = "https://test.echo/teas/task"
            def urlProd = "https://prod.echo/teas/task"

            // Variables para ambiente y credenciales
            def ambiente = ""
            def urlTarget = ""
            def credencialId = ""

            // Selección del ambiente según el nombre del pipeline
            if (projectName.toUpperCase().contains("DEV")) {
                ambiente = "DEV"
                credencialId = "ConsolaTeaTibcoDEV"
                urlTarget = urlDev
            } else if (projectName.toUpperCase().contains("TEST")) {
                ambiente = "TEST"
                credencialId = "ConsolaTeaTibcoTEST"
                urlTarget = urlTest
            } else {
                ambiente = "PROD"
                credencialId = "ConsolaTeaTibcoPROD"
                urlTarget = urlProd
            }

            // Mostrar en consola el ambiente seleccionado
            command.echo "🌐 Ambiente detectado: ${ambiente}"
            command.echo "🔐 Usando credenciales: ${credencialId}"
            command.echo "📡 URL objetivo: ${urlTarget}"

            // Ejecución del comando CURL con las credenciales del ambiente
            command.withCredentials([
                command.usernamePassword(
                    credentialsId: credencialId,
                    usernameVariable: 'userNameTibco',
                    passwordVariable: 'passwordTibco'
                )
            ]) {
                command.sh """
                    curl -k -u $userNameTibco:$passwordTibco -X PUT '${urlTarget}' \
                    --header 'Content-Type: application/json' \
                    --data '{
                        "operation": "createUserWithReferenceReturn",
                        "params": {
                            "name": "${usuario}",
                            "password": "${password}",
                            "groups": [],
                            "roles": ["BW Administrator","BW Operator","BW User","TEA_ADMIN"],
                            "objectId": "tea:tea:users"
                        },
                        "methodType": "UPDATE"
                    }'
                """
            }

        } catch (e) {
            commonStgs.printOutput("${e}", "R")
        }
    }
}


