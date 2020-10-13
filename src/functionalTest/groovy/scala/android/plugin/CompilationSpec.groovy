package scala.android.plugin


import scala.android.plugin.internal.AndroidFunctionalSpec

class CompilationSpec extends AndroidFunctionalSpec {

    def setup() {
        file("settings.gradle") << "rootProject.name = 'test-app'"
        createBuildFileForApplication()
        createAndroidManifest()
        createMainActivityLayoutFile()

        // create Java class to ensure this compile correctly along with scala classes
        file('src/main/java/scala/android/test/SimpleJava.java') << """
            package scala.android.test;
            
            public class SimpleJava {
                public static int getInt() {
                    return 1337;
                }
            }
        """

        file('src/main/scala/scala/android/test/MainActivity.scala') << """
            package scala.android.test
            import android.app.Activity
            import android.os.Bundle
            import cats.effect.IO
         
            class MainActivity extends Activity {
                override def onCreate(savedInstanceState:Bundle):Unit = {
                    super.onCreate(savedInstanceState)
                    val program = IO {
                        val contentView = R.layout.activity_main
                        val someValue = SimpleJava.getInt()
                        val result = someValue * SimpleJava.getInt()
                    }
                    
                    program.unsafeRunSync()
                }
            }
        """

        file('src/test/scala/scala/android/test/JvmTest.scala') << """
            package scala.android.test
            import org.junit.Test
            import org.junit.Assert._
            class JvmTest {
                @Test def shouldCompile():Unit = {
                    assertEquals(10*2, 20)
                }
            }
        """
    }

    def "compile android app"() {
        when:

        run('assemble', 'test')

        then:
        noExceptionThrown()
        file("build/intermediates/javac/debug/classes/scala/android/test/MainActivity.class").exists()
        file("build/intermediates/javac/debugUnitTest/classes/scala/android/test/JvmTest.class").exists()
        file("build/intermediates/javac/releaseUnitTest/classes/scala/android/test/JvmTest.class").exists()
    }
}
