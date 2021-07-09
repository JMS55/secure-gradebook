import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import java.util.List;

public class CLIUtils {
    public static <T> T getLast(List<T> list) {
        return list.get(list.size() - 1);
    }

    public static class GradebookFileNameValidator implements IParameterValidator {
        public void validate(String name, String value) throws ParameterException {
            if (!value.matches("[a-zA-Z_.]+")) {
                throw new ParameterException("Parameter " + name + " is invalid");
            }
        }
    }

    public static class KeyValidator implements IParameterValidator {
        public void validate(String name, String value) throws ParameterException {
            if (!value.matches("[a-fA-F0-9]{64}")) {
                throw new ParameterException("Parameter " + name + " is invalid");
            }
        }
    }

    public static class AssignmentNameValidator implements IParameterValidator {
        public void validate(String name, String value) throws ParameterException {
            if (!value.matches("[a-zA-Z0-9]+")) {
                throw new ParameterException("Parameter " + name + " is invalid");
            }
        }
    }

    public static class NonNegativeIntegerValidator implements IParameterValidator {
        public void validate(String name, String value) throws ParameterException {
            try {
                int n = Integer.parseInt(value);
                if (n < 0) {
                    throw new ParameterException("Parameter " + name + " is invalid");
                }
            } catch (NumberFormatException ignored) {
                throw new ParameterException("Parameter " + name + " is invalid");
            }
        }
    }

    public static class AssignmentWeightValidator implements IParameterValidator {
        public void validate(String name, String value) throws ParameterException {
            try {
                double n = Double.parseDouble(value);
                if (!(n >= 0.0 && n <= 1.0)) {
                    throw new ParameterException("Parameter " + name + " is invalid");
                }
            } catch (NumberFormatException ignored) {
                throw new ParameterException("Parameter " + name + " is invalid");
            }
        }
    }

    public static class StudentNameValidator implements IParameterValidator {
        public void validate(String name, String value) throws ParameterException {
            if (!value.matches("[a-zA-Z]+")) {
                throw new ParameterException("Parameter " + name + " is invalid");
            }
        }
    }
}
