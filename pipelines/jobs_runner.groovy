import java.util.List

def jobs = [] // Инициализация переменной на уровне всего скрипта
def testsStatistic = [:] // Инициализация переменной для статистики тестов

timeout(300) {
    node("maven") {

        withBuildUser {
            def user = env.BUILD_USER
            currentBuild.description = "Running: $user"
        }

        def yamlConfig = readYaml text: YAML_CONFIG
        def testTypes = yamlConfig['TEST_TYPES']

        def testsRunning = [:] // Инициализация карты для параллельного выполнения

        testTypes.each { type ->
            testsRunning[type] = {
                node('maven') {
                    stage("Running $type") {
                        def jobResult = build(job: "${type}", propagate: false, wait: true)
                        jobs.add(jobResult) // Добавляем результат выполнения задачи в список

                        // Пример добавления статистики тестов
                        testsStatistic[type] = jobResult.result // Сохраняем результат теста
                    }
                }
            }
        }

        parallel testsRunning // Запуск параллельных задач

        stage("Publish allure report") {
            try {
                jobs.each { job ->
                    def upstreamProject = job.getParent().getFullName() // Получаем имя проекта
                    def upstreamBuild = job.getId() // Получаем ID сборки

                    copyArtifacts filter: "**/allure-results", projectName: upstreamProject, selector: specific("${upstreamBuild}")
                }

                allure([
                    includeProperties: false,
                    jdk              : '',
                    properties       : [],
                    reportBuildPolicy: 'ALWAYS',
                    results          : [[path: './app/allure-results']]
                ])
            } catch (Exception e) {
                echo "Error during publishing allure report: ${e.message}"
            }

            stage("Telegram notification") {
                def message = """============ ALL TESTS RESULT ==============
    """
                testsStatistic.each { k, v ->
                    message += "\t\t$k: $v\n"
                }

                withCredentials([string(credentialsId: 'chat_id', variable: 'chatId'), string(credentialsId: 'bot_token', variable: 'botToken')]) {
                    sh "curl -X POST -H 'Content-Type: application/json' -d '{\"chat_id\": \"$chatId\", \"text\": \"$message\"}' \"https://api.telegram.org/bot$botToken/sendMessage\""
                }
            }
        }
    }
}