package picocli;

import org.junit.Test;
import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import static org.junit.Assert.*;

public class ParameterConsumerTest {

    static class FindExecParameterConsumer implements IParameterConsumer {
        public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
            List<String> list = argSpec.getValue();
            if (list == null) { // may not be needed if the option is always initialized with non-null value
                list = new ArrayList<String>();
                argSpec.setValue(list);
            }
            while (!args.isEmpty()) {
                String arg = args.pop();
                list.add(arg);

                // `find -exec` semantics: stop processing after a ';' or '+' argument
                if (";".equals(arg) || "+".equals(arg)) {
                    break;
                }
            }
        }
    }

    @Test
    public void testSimpleParameterConsumer() {
        class App {
            @Option(names = "-x", parameterConsumer = FindExecParameterConsumer.class)
            List<String> list = new ArrayList<String>();

            @Option(names = "-y") boolean y;
        }

        App app = CommandLine.populateCommand(new App(), "-x a b -y c d".split(" "));
        assertEquals(Arrays.asList("a", "b", "-y", "c", "d"), app.list);
    }

    @Test
    public void testFindExec() {
        class Find {
            @Option(names = "-exec", parameterConsumer = FindExecParameterConsumer.class)
            List<String> list = new ArrayList<String>();

            @Option(names = "-r") boolean recursive;
        }

        Find semicolon = CommandLine.populateCommand(new Find(), "-exec ls -r {} ;".split(" "));
        assertEquals(Arrays.asList("ls", "-r", "{}", ";"), semicolon.list);
        assertFalse(semicolon.recursive);

        Find plus = CommandLine.populateCommand(new Find(), "-exec ls -r {} +".split(" "));
        assertEquals(Arrays.asList("ls", "-r", "{}", "+"), plus.list);
        assertFalse(plus.recursive);

        Find additionalInput = CommandLine.populateCommand(new Find(), "-exec ls -r {} + -r".split(" "));
        assertEquals(Arrays.asList("ls", "-r", "{}", "+"), additionalInput.list);
        assertTrue(additionalInput.recursive);
    }

    @Test
    public void testSimplePositionalParameterConsumer() {
        class App {
            @Parameters(index = "0..*", parameterConsumer = FindExecParameterConsumer.class)
            List<String> list = new ArrayList<String>();

            @Option(names = "-y") boolean y;
        }

        App app = CommandLine.populateCommand(new App(), "000 a b -y c d".split(" "));
        assertEquals(Arrays.asList("000", "a", "b", "-y", "c", "d"), app.list);
    }

    @Test
    public void testIndexOfPositionalParameterConsumer() {
        class App {
            @Parameters(index = "0", parameterConsumer = FindExecParameterConsumer.class)
            List<String> list = new ArrayList<String>();

            @Parameters(index = "1..*", parameterConsumer = FindExecParameterConsumer.class)
            List<String> remainder = new ArrayList<String>();

            @Option(names = "-y") boolean y;
        }

        App app = CommandLine.populateCommand(new App(), "00 a b ; c d".split(" "));
        assertEquals(Arrays.asList("00", "a", "b", ";"), app.list);
        assertEquals(Arrays.asList("c", "d"), app.remainder);
        assertFalse(app.y);

        App other = CommandLine.populateCommand(new App(), "0 a b ; -y c d".split(" "));
        assertEquals(Arrays.asList("0", "a", "b", ";"), other.list);
        assertEquals(Arrays.asList("c", "d"), other.remainder);
        assertTrue(other.y);
    }
}
