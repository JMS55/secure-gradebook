import at.favre.lib.bytes.Bytes;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.sqlite.*;

public class Gradebook {
    private final String name;
    private SecretKey key;
    private Connection db;

    private Gradebook(String name) {
        this.name = name;
        this.key = null;
        this.db = null;
    }

    public static class AppError extends Exception {
    }

    public static class BackendError extends Exception {
    }

    public static String create(String name) throws AppError, BackendError {
        if (new File(name).exists()) {
            throw new AppError();
        }

        try {
            Gradebook gbook = new Gradebook(name);
            gbook.db = DriverManager.getConnection("jdbc:sqlite:" + name);

            gbook.db.prepareStatement(
                    "CREATE TABLE assignments(assignmentID INTEGER PRIMARY KEY AUTOINCREMENT, assignmentName TEXT NOT NULL UNIQUE, points INTEGER NOT NULL, weight REAL NOT NULL);")
                    .execute();

            gbook.db.prepareStatement(
                    "CREATE TRIGGER validate_total_assignment_weights BEFORE INSERT ON assignments BEGIN SELECT CASE WHEN (SELECT SUM(weight) FROM assignments) + NEW.weight > 1.0 THEN RAISE (ABORT, \"Assignment weights above 1.0\") END; END;")
                    .execute();

            gbook.db.prepareStatement(
                    "CREATE TABLE students(studentID INTEGER PRIMARY KEY AUTOINCREMENT, firstName TEXT NOT NULL, lastName TEXT NOT NULL, UNIQUE(firstName, lastName));")
                    .execute();

            gbook.db.prepareStatement(
                    "CREATE TABLE grades(gradeID INTEGER PRIMARY KEY AUTOINCREMENT, studentID INTEGER NOT NULL, assignmentID INTEGER NOT NULL, grade INTEGER NOT NULL,"
                            + "FOREIGN KEY (studentID) REFERENCES students (studentID) ON DELETE CASCADE, FOREIGN KEY (assignmentID) REFERENCES assignments (assignmentID) ON DELETE CASCADE,"
                            + "UNIQUE(studentID, assignmentID));")
                    .execute();

            gbook.db.prepareStatement(
                    "CREATE TRIGGER validate_grade BEFORE UPDATE ON grades BEGIN SELECT CASE WHEN NEW.grade > (SELECT points FROM assignments WHERE assignmentID = NEW.assignmentID) THEN RAISE (ABORT, \"Grade above assignment points\") END; END;")
                    .execute();

            KeyGenerator keyGen = KeyGenerator.getInstance("ChaCha20");
            keyGen.init(256, SecureRandom.getInstanceStrong());
            byte[] bkey = keyGen.generateKey().getEncoded();
            gbook.key = new SecretKeySpec(bkey, "ChaCha20");

            gbook.save_and_encrypt();

            return Bytes.wrap(bkey).encodeHex();
        } catch (NoSuchAlgorithmException | SQLException ignored) {
            throw new BackendError();
        }
    }

    public void save_and_encrypt() throws BackendError {
        try {
            this.db.close();

            Path file = Paths.get(this.name);

            byte[] nonce = new byte[12];
            SecureRandom.getInstanceStrong().nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            IvParameterSpec iv = new IvParameterSpec(nonce);
            cipher.init(Cipher.ENCRYPT_MODE, this.key, iv);

            byte[] data = Files.readAllBytes(file);
            byte[] out = cipher.doFinal(data);

            Files.write(file, nonce);
            Files.write(file, out, StandardOpenOption.APPEND);
        } catch (IOException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException
                | SQLException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException ignored) {
            throw new BackendError();
        }
    }

    public static Gradebook load_and_decrypt(String name, String key) throws AppError, BackendError {
        if (!new File(name).exists()) {
            throw new AppError();
        }

        try {
            Gradebook gbook = new Gradebook(name);
            byte[] bkey = Bytes.parseHex(key).array();
            gbook.key = new SecretKeySpec(bkey, "ChaCha20");

            Path file = Paths.get(name);

            byte[] data = Files.readAllBytes(file);
            if (data.length < 12) {
                throw new AppError();
            }

            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            IvParameterSpec iv = new IvParameterSpec(data, 0, 12);
            cipher.init(Cipher.DECRYPT_MODE, gbook.key, iv);

            byte[] out = cipher.doFinal(data, 12, data.length - 12);
            Files.write(file, out);

            gbook.db = DriverManager.getConnection("jdbc:sqlite:" + name);
            return gbook;
        } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | InvalidAlgorithmParameterException | IllegalBlockSizeException ignored) {
            throw new BackendError();
        } catch (BadPaddingException | SQLException ignored) {
            throw new AppError();
        }
    }

    public void addAssignment(String name, int points, double weight) throws AppError, BackendError {
        try {
            PreparedStatement sql = this.db
                    .prepareStatement("INSERT INTO assignments (assignmentName, points, weight) VALUES (?1, ?2, ?3);");
            sql.setString(1, name);
            sql.setInt(2, points);
            sql.setDouble(3, weight);
            sql.execute();

            sql = this.db.prepareStatement(
                    "INSERT INTO grades (studentID, assignmentID, grade) SELECT studentID, (SELECT assignmentID from assignments WHERE assignmentName = ?1), 0 FROM students;");
            sql.setString(1, name);
            sql.execute();
        } catch (SQLiteException e) {
            if (e.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE
                    || e.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER) {
                throw new AppError();
            } else {
                throw new BackendError();
            }
        } catch (SQLException ignored) {
            throw new BackendError();
        }
    }

    public void deleteAssignment(String name) throws AppError, BackendError {
        try {
            PreparedStatement sql = this.db.prepareStatement("DELETE FROM assignments WHERE assignmentName = ?1;");
            sql.setString(1, name);
            if (sql.executeUpdate() == 0) {
                throw new AppError();
            }
        } catch (SQLException ignored) {
            throw new BackendError();
        }
    }

    public void addStudent(String firstName, String lastName) throws AppError, BackendError {
        try {
            PreparedStatement sql = this.db
                    .prepareStatement("INSERT INTO students (firstName, lastName) VALUES (?1, ?2);");
            sql.setString(1, firstName);
            sql.setString(2, lastName);
            sql.execute();

            sql = this.db.prepareStatement(
                    "INSERT INTO grades (studentID, assignmentID, grade) SELECT (SELECT studentID from students WHERE firstName = ?1 AND lastName = ?2), assignmentID, 0 FROM assignments;");
            sql.setString(1, firstName);
            sql.setString(2, lastName);
            sql.execute();
        } catch (SQLiteException e) {
            if (e.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE) {
                throw new AppError();
            } else {
                throw new BackendError();
            }
        } catch (SQLException ignored) {
            throw new BackendError();
        }
    }

    public void deleteStudent(String firstName, String lastName) throws AppError, BackendError {
        try {
            PreparedStatement sql = this.db
                    .prepareStatement("DELETE FROM students WHERE firstName = ?1 AND lastName = ?2;");
            sql.setString(1, firstName);
            sql.setString(2, lastName);
            if (sql.executeUpdate() == 0) {
                throw new AppError();
            }
        } catch (SQLException ignored) {
            throw new BackendError();
        }
    }

    public void addGrade(String studentFirstName, String studentLastName, String assignmentName, int grade)
            throws AppError, BackendError {
        try {
            PreparedStatement sql = this.db.prepareStatement(
                    "UPDATE grades SET grade = ?1 WHERE studentID = (SELECT studentID FROM students WHERE firstName = ?2 AND lastName = ?3) AND assignmentID = (SELECT assignmentID FROM assignments WHERE assignmentName = ?4);");
            sql.setInt(1, grade);
            sql.setString(2, studentFirstName);
            sql.setString(3, studentLastName);
            sql.setString(4, assignmentName);
            if (sql.executeUpdate() == 0) {
                throw new AppError();
            }
        } catch (SQLiteException e) {
            if (e.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER) {
                throw new AppError();
            } else {
                throw new BackendError();
            }
        } catch (SQLException ignored) {
            throw new BackendError();
        }
    }

    public void printAssignmentGrades(String name, boolean alphabetical_or_grade_order) throws AppError, BackendError {
        try {
            PreparedStatement sql = this.db
                    .prepareStatement("SELECT assignmentID FROM assignments WHERE assignmentName = ?1;");
            sql.setString(1, name);
            ResultSet row = sql.executeQuery();
            if (!row.isBeforeFirst()) {
                throw new AppError();
            }
            int assignmentID = row.getInt(1);

            String order = "lastName, firstName";
            if (!alphabetical_or_grade_order) {
                order = "grade DESC";
            }

            sql = this.db.prepareStatement("SELECT lastName, firstName, grade FROM grades "
                    + "INNER JOIN students ON grades.studentID = students.studentID "
                    + "WHERE assignmentID = ?1 ORDER BY " + order + ";");
            sql.setInt(1, assignmentID);
            row = sql.executeQuery();
            while (row.next()) {
                System.out.println("(" + row.getString(1) + ", " + row.getString(2) + ", " + row.getInt(3) + ")");
            }
        } catch (SQLException ignored) {
            throw new BackendError();
        }
    }

    public void printStudentGrades(String firstName, String lastName) throws AppError, BackendError {
        try {
            PreparedStatement sql = this.db
                    .prepareStatement("SELECT studentID FROM students WHERE firstName = ?1 AND lastName = ?2;");
            sql.setString(1, firstName);
            sql.setString(2, lastName);
            ResultSet row = sql.executeQuery();
            if (!row.isBeforeFirst()) {
                throw new AppError();
            }
            int studentID = row.getInt(1);

            sql = this.db.prepareStatement(
                    "SELECT assignmentName, grade FROM grades INNER JOIN assignments ON grades.assignmentID = assignments.assignmentID WHERE studentID = ?1;");
            sql.setInt(1, studentID);
            row = sql.executeQuery();
            while (row.next()) {
                System.out.println("(" + row.getString(1) + ", " + row.getInt(2) + ")");
            }
        } catch (SQLException ignored) {
            throw new BackendError();
        }
    }

    public void printFinalGrades(boolean alphabetical_or_grade_order) throws BackendError {
        try {
            String order = "lastName, firstName";
            if (!alphabetical_or_grade_order) {
                order = "totalGrade DESC";
            }

            PreparedStatement sql = this.db.prepareStatement(
                    "SELECT lastname, firstName, (SELECT SUM((CAST(grade AS REAL) / points) * weight) FROM grades INNER JOIN assignments ON grades.assignmentID = assignments.assignmentID WHERE grades.studentID = students.studentID) AS totalGrade FROM students ORDER BY "
                            + order + ";");
            ResultSet row = sql.executeQuery();
            while (row.next()) {
                System.out.println("(" + row.getString(1) + ", " + row.getString(2) + ", " + row.getDouble(3) + ")");
            }
        } catch (SQLException ignored) {
            throw new BackendError();
        }
    }
}
