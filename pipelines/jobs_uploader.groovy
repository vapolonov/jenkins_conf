@Field
def JOBS_DIR = "${WORKSPACE}/jobs"

@Field
def CONFIG_FILE = "${WORKSPACE}/uploader.ini"

node('maven') {

    stage('Checkout') {
        checkout scm
    }

    stage('Create uploader.ini') {
        withCredentials([usernamePassword(credentialsId: "uploader",
                passwordVariable: 'pass', usernameVariable: 'user')]) {
            sh """
        cat > ${CONFIG_FILE} <<'EOF'
[jenkins]
url=http://localhost/
user=$user
password=$pass

[job_builder]
recursive=True
keep_descriptions=False
EOF
        """
        }
    }

    stage('Run Upload Script') {
        sh "jenkins-jobs --conf  ${CONFIG_FILE} --flush-cache update ${JOBS_DIR}"
    }
}