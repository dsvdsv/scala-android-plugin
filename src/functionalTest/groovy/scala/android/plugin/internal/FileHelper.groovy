package scala.android.plugin.internal


trait FileHelper {

    File file(String path) {
        def file = new File(getTestProjectDir(), path)
        assert file.parentFile.mkdirs() || file.parentFile.exists()
        return file
    }

    abstract File getTestProjectDir()
}