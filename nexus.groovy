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
