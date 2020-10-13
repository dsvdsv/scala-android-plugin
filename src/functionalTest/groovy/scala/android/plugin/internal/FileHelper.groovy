package scala.android.plugin.internal

import org.junit.rules.TemporaryFolder

trait FileHelper {

    File file(String path) {
        def file = new File(getTestProjectDir().root, path)
        assert file.parentFile.mkdirs() || file.parentFile.exists()
        return file
    }

    abstract TemporaryFolder getTestProjectDir()
}