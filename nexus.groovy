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


void obtenerDeploymentInfo(def servidoresPorApp, String app, String aplicativo) {
    command.stage("Obtener deployment-info") {
        try {
            command.currentBuild.displayName = "Aplicación: ${app}"
            command.currentBuild.description = "Consulta deployments en JBoss"

            servidoresPorApp.each { apps ->
                if (apps.app.trim().equalsIgnoreCase(app.trim())) {
                    try {
                        def salida = command.sh(
                            script: """
                                ssh brandon@${apps.ip} 'sudo -u bra /opt/jboss-eap/bin/jboss-cli.sh -c --commands="deployment-info"'
                            """,
                            returnStdout: true
                        ).trim()

                        commonStgs.printOutput("Salida obtenida de ${apps.ip}:", "G")
                        commonStgs.printOutput(salida, "B")  // imprime todo el resultado en azul, por ejemplo

                        // Retorna la salida para siguientes etapas
                        return salida

                    } catch (e) {
                        commonStgs.printOutput("Error al obtener deployment-info en ${apps.ip}: ${e.message}", "R")
                    }
                }
            }

        } catch (e) {
            commonStgs.printOutput("Error general en obtenerDeploymentInfo: ${e.message}", "R")
        }
    }
}


