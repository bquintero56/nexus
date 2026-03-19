

\"server_name\"\s*:\s*\"S11\".*\"ip\"\s*:\s*\"10\.100\.34\.34\".*\"servicio\"\s*:\s*\"httpd\.service\".*\"resultadoServicio\"\s*:\s*true





$[?(
  @.server_name=="Myserver" &&
  @.ip=="10.10.10.1" &&
  @.servicio=="h.service" &&
  @.resultadoServicio==true &&
  @.url==null &&
  @.resultadoUrl==true
)]







stage('Subir Archivo JSON a Nexus') {
    steps {
        script {

            def jsonContent = []

            servers.each { server ->

                def srvRes = serviceResults.find { 
                    it.server_name == server.server_name &&
                    it.service == server.service &&
                    it.ip == server.ip   // 🔥 IMPORTANTE: incluir IP
                }

                def urlRes = urlResults.find { 
                    it.server_name == server.server_name &&
                    it.url == server.url &&
                    it.ip == server.ip   // 🔥 IMPORTANTE
                }

                jsonContent << [
                    server_name: server.server_name,
                    ip: server.ip,                       // ✅ AGREGADO
                    servicio: srvRes?.service,
                    resultadoServicio: srvRes?.success,
                    url: urlRes?.url,
                    resultadoUrl: urlRes?.success
                ]
            }

            def jsonFile = "estado_servidores.json"

            // ✅ JSON limpio, sin problemas de sandbox ni serialización
            def json = new groovy.json.JsonBuilder(jsonContent).toPrettyString()

            writeFile file: jsonFile, text: json

            echo "Archivo creado: ${jsonFile}"
            echo json

            def token = "ot:Ortd"
            def urlNexus = "https://.../estado_servidores.json"

            wrap([$class: 'MaskPasswordsBuildWrapper',
                varPasswordPairs: [[password: token]]
            ]) {
                sh """
                curl -s --insecure -v -u ${token} \
                --upload-file ${jsonFile} ${urlNexus}
                """
            }
        }
    }
}
































stage('Subir Archivo JSON a Nexus') {
    steps {
        script {

            def jsonContent = []

            servers.each { server ->

                def srvRes = serviceResults.find { 
                    it.server_name == server.server_name && it.service == server.service 
                }

                def urlRes = urlResults.find { 
                    it.server_name == server.server_name && it.url == server.url 
                }

                jsonContent << [
                    server_name: server.server_name,
                    servicio: srvRes?.service,
                    resultadoServicio: srvRes?.success,
                    url: urlRes?.url,
                    resultadoUrl: urlRes?.success
                ]
            }

            def jsonFile = "estado_servidores.json"

            // ✅ AQUÍ ESTÁ LA CLAVE
            def json = groovy.json.JsonOutput.toJson(jsonContent)

            writeFile file: jsonFile, text: json

            echo "Archivo creado: ${jsonFile}"
            echo groovy.json.JsonOutput.prettyPrint(json)

            def token = "ot:Ortd"
            def urlNexus = "https://.../estado_servidores.json"

            wrap([$class: 'MaskPasswordsBuildWrapper',
                varPasswordPairs: [[password: token]]
            ]) {
                sh """
                curl -s --insecure -v -u ${token} \
                --upload-file ${jsonFile} ${urlNexus}
                """
            }
        }
    }
}


























































import groovy.json.JsonSlurper

def jsonTmp = new JsonSlurper().parseText(getHostsCommand)

// Copia profunda a estructuras serializables
def json = [:] + jsonTmp
jsonTmp = null

def hostConnectionsTmp = json.result['host-connection']
def hostConnections = [:] + hostConnectionsTmp
hostConnectionsTmp = null

def hosts = []
hostConnections.each { k, v ->
    if (v.connected == true) {
        hosts << k.toString()
    }
}

// 🔥 eliminar referencias problemáticas
json = null
hostConnections = null

commonStgs.printOutput("Los hosts son: ${hosts}", "G")

void crearComando(def servidoresPorApp) {
    command.stage("Validar WAR detenidos (por columnas)") {
        try {
            command.currentBuild.displayName = "Validar WAR detenidos"
            command.currentBuild.description = "Lee columnas NAME/STATUS y lista .war STOPPED"

            servidoresPorApp.each { apps ->
                try {
                    // 1) Obtener salida del CLI remoto
                    def salida = command.sh(
                        script: """
                            ssh -o StrictHostKeyChecking=no brandon@${apps.ip} \\
                                'sudo -u ${apps.user ?: "bra"} /opt/jboss-eap/bin/jboss-cli.sh -c --commands="deployment-info"'
                        """,
                        returnStdout: true
                    ).toString()

                    // 2) Guardar salida en un TXT del workspace (uno por servidor)
                    String fileName = "deploy-info-${apps.ip}.txt"
                    command.writeFile(file: fileName, text: salida)
                    command.echo "📝 Guardado: ${fileName}"

                    // 3) Leer líneas y localizar columnas por encabezado
                    List<String> lines = salida.replace("\r","").readLines()
                    int headerIdx = lines.findIndexOf { it.contains("NAME") && it.contains("STATUS") }
                    if (headerIdx < 0) {
                        command.echo "⚠️ No se encontró encabezado con columnas NAME/STATUS en ${apps.ip}"
                        return
                    }

                    String header = lines[headerIdx]
                    int colName    = header.indexOf("NAME")
                    int colStatus  = header.indexOf("STATUS")

                    if (colName < 0 || colStatus < 0) {
                        command.echo "⚠️ Encabezado inesperado en ${apps.ip}: '${header}'"
                        return
                    }

                    // 4) Recorrer filas de datos y extraer NAME/STATUS por posiciones
                    List<String> warsDetenidos = []
                    lines.drop(headerIdx + 1).each { ln ->
                        if (!ln?.trim()) return
                        // Evitar líneas separadoras tipo "-----"
                        if (ln.trim().startsWith("-")) return

                        // Asegurar longitud
                        String padded = ln
                        if (padded.length() < colStatus+1) padded = padded.padRight(colStatus+1, ' ' as char)

                        String name   = padded.substring(colName,   Math.min(colStatus, padded.length())).trim()
                        String status = padded.substring(colStatus).trim()

                        if (name.toLowerCase().endsWith(".war") && status.toUpperCase().contains("STOPPED")) {
                            warsDetenidos << name
                        }
                    }

                    // 5) Mostrar resultado
                    if (warsDetenidos) {
                        command.echo "🚫 WARs detenidos en ${apps.ip}:"
                        warsDetenidos.unique().each { command.echo " - ${it}" }
                    } else {
                        command.echo "✅ No se encontraron WARs detenidos en ${apps.ip}."
                    }

                } catch (e) {
                    command.echo "❌ Error en ${apps.ip}: ${e.message}"
                }
            }

        } catch (e) {
            command.echo "❌ Error general: ${e.message}"
        }
    }
}

sh '''
ssh brandon@18.737.373 "sudo -u braditon bash -c \\"sed -i 's/\\\\.war\\\">/\\\\.war\\\" enabled=\\\"false\\\">/g' /opt/jboss-eap/standalone/configuration/standalone.xml\\""
'''

sh '''
ssh brandon@18.737.373 "sudo -u braditon bash -c 'sed -i \\'s/\\.war\\\">/\\.war\\\" enabled=\\\"false\\\">/g\\' /opt/jboss-eap/standalone/configuration/standalone.xml'"
'''



sh '''
ssh brandon@18.737.373 "sudo -u braditon bash -c 'sed -i s/\\.war\\\">/\\.war\\\"\\ enabled=\\\"false\\\">/g /opt/jboss-eap/standalone/configuration/standalone.xml'"
'''


sh '''
ssh brandon@18.737.373 "sudo -u braditon bash -c 'sed -i \"s/\\.war\\\">/\\.war\\\" enabled=\\\"false\\\">/g\" /opt/jboss-eap/standalone/configuration/standalone.xml'"
'''

sh '''
ssh brandon@18.737.373 "sudo -u braditon bash -c 'sed -i \\'s/\\.war\\\">/\\.war\\\" enabled=\\\"false\\\">/g\\' /opt/jboss-eap/standalone/configuration/standalone.xml'"
'''




sh '''
# 1️⃣ Crear el script remoto con el comando sed
ssh brandon@18.737.373 "cat > /tmp/actualizar_war.sh <<'EOF'
#!/bin/bash
# Deshabilitar los WAR en el standalone.xml
sed -i 's/\\.war\">/\\.war\" enabled=\"false\">/g' /opt/jboss-eap/standalone/configuration/standalone.xml
EOF"

# 2️⃣ Darle permisos de ejecución
ssh brandon@18.737.373 "chmod +x /tmp/actualizar_war.sh"

# 3️⃣ Ejecutarlo como el usuario braditon
ssh brandon@18.737.373 "sudo -u braditon bash /tmp/actualizar_war.sh"
'''


ssh brandon@18.737.373 "cat > /tmp/actualizar_war.sh <<'EOF'
#!/bin/bash
# 🔧 Script para deshabilitar WARs en JBoss
sed -i 's/\.war\">/\.war\" enabled=\"false\">/g' /opt/jboss-eap/standalone/configuration/standalone.xml
EOF"


sh '''
ssh brandon@18.737.373 "echo '#!/bin/bash
# 🔧 Script para deshabilitar WARs en JBoss
sed -i \\'s/\\\\.war\\\">/\\\\.war\\\" enabled=\\\\\"false\\\\\">/g\\' /opt/jboss-eap/standalone/configuration/standalone.xml
' > /tmp/actualizar_war.sh"

ssh brandon@18.737.373 "chmod +x /tmp/actualizar_war.sh"
ssh brandon@18.737.373 "sudo -u braditon bash /tmp/actualizar_war.sh"
'''


/deployment=miapp.war/subsystem=undertow:read-resource(include-runtime=true, recursive=true)


