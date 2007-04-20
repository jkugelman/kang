using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Text;

namespace JohnKugelman
{
    /// Global logging class. Displays debugging messages to one or more
    /// <see cref="TextWriter"/>s, usually <see cref="Console.Out"/> and
    /// <see cref="Console.Err"/>. Each message is annotated with the file and
    /// line number of the log statement and a timestamp.
    ///
    /// There are six levels of messages:  Debug, Error, Warning, Major, Minor,
    /// and Verbose. Only messages at or above the default logging level will be
    /// displayed. This level can be overridden for specific namespaces or
    /// classes for fine-grained control over logging output.
    public static class Log
    {
        public enum Level
        {
            Debug,
            Error,
            Warning,
            Major,
            Minor,
            Verbose
        }
    
        private static Level defaultLevel;
        private static SortedList<string, Level> overriddenLevels;
        private static TextWriter outputWriter, errorWriter;

        static Log()
        {
            defaultLevel     = Level.Major;
            overriddenLevels = new SortedList<string, Level>();
            outputWriter     = Console.Out;
            errorWriter      = Console.Error;
            
            // Always show warnings and errors from this class.
            OverrideLevel(typeof(Log).FullName, Level.Warning);
        }

        public static Level DefaultLevel
        {
            get { return defaultLevel;  }
            set { defaultLevel = value; }
        }

        public static Level LevelFor(string identifier)
        {
            Level level = defaultLevel;
            
            foreach (KeyValuePair<string, Level> pair in overriddenLevels)
            {
                if (identifier.StartsWith(pair.Key))
                    level = pair.Value;
            }
            
            return level;
        }

        public static void OverrideLevel(string identifier, Level level)
        {
            overriddenLevels[identifier] = level;
        }

        public static TextWriter OutputWriter
        {
            get { return outputWriter;  }
            set { outputWriter = value; }
        }

        public static TextWriter ErrorWriter
        {
            get { return errorWriter;  }
            set { errorWriter = value; }
        }
    
        public static void logDebug  (                     string format, params object[] arguments) { log(new StackFrame(1, true), Level.Debug,              format, arguments); }
        public static void logError  (                     string format, params object[] arguments) { log(new StackFrame(1, true), Level.Error,              format, arguments); }
        public static void logWarning(                     string format, params object[] arguments) { log(new StackFrame(1, true), Level.Warning,            format, arguments); }
        public static void logMajor  (                     string format, params object[] arguments) { log(new StackFrame(1, true), Level.Major,              format, arguments); }
        public static void logMinor  (                     string format, params object[] arguments) { log(new StackFrame(1, true), Level.Minor,              format, arguments); }
        public static void logVerbose(                     string format, params object[] arguments) { log(new StackFrame(1, true), Level.Verbose,            format, arguments); }

        public static void logDebug  (Exception exception, string format, params object[] arguments) { log(new StackFrame(1, true), Level.Debug,   exception, format, arguments); }
        public static void logError  (Exception exception, string format, params object[] arguments) { log(new StackFrame(1, true), Level.Error,   exception, format, arguments); }
        public static void logWarning(Exception exception, string format, params object[] arguments) { log(new StackFrame(1, true), Level.Warning, exception, format, arguments); }
        public static void logMajor  (Exception exception, string format, params object[] arguments) { log(new StackFrame(1, true), Level.Major,   exception, format, arguments); }
        public static void logMinor  (Exception exception, string format, params object[] arguments) { log(new StackFrame(1, true), Level.Minor,   exception, format, arguments); }
        public static void logVerbose(Exception exception, string format, params object[] arguments) { log(new StackFrame(1, true), Level.Verbose, exception, format, arguments); }
    
        public static void logDebug  (Exception exception)                                           { log(new StackFrame(1, true), Level.Debug,   exception);                    }
        public static void logError  (Exception exception)                                           { log(new StackFrame(1, true), Level.Error,   exception);                    }
        public static void logWarning(Exception exception)                                           { log(new StackFrame(1, true), Level.Warning, exception);                    }
        public static void logMajor  (Exception exception)                                           { log(new StackFrame(1, true), Level.Major,   exception);                    }
        public static void logMinor  (Exception exception)                                           { log(new StackFrame(1, true), Level.Minor,   exception);                    }
        public static void logVerbose(Exception exception)                                           { log(new StackFrame(1, true), Level.Verbose, exception);                    }

        private static void log(StackFrame caller, Level level, string format, params object[] arguments)
        {
            writeMessage(caller, level, string.Format(format, arguments), null);
        }
    
        public static void log(StackFrame caller, Level level, Exception exception, string format, params object[] arguments)
        {
            writeMessage(caller, level, string.Format(format, arguments), exception.ToString());
        }
    
        public static void log(StackFrame caller, Level level, Exception exception)
        {
            writeMessage(caller, level, null, exception.ToString());
        }

        private static void writeMessage(StackFrame caller, Level level, string message, string details)
        {
            if (!isVisible(caller, level))
                return;
        
            string     output = buildOutput(caller, level, message, details);
            TextWriter writer = writerFor(level);
        
            lock (writer)
            {
                writer.Write(output);
            }
        }

        private static bool isVisible(StackFrame caller, Level level)
        {
            MethodBase callerMethod = caller.GetMethod();
            string     callerName   = callerMethod.DeclaringType.FullName + "." + callerMethod.Name;
        
            return level <= LevelFor(callerName);
        }
    
        private static string buildOutput(StackFrame caller, Level level, string message, string details)
        {
            StringBuilder outputBuilder = new StringBuilder();
            
            outputBuilder.AppendFormat("{0} {1} ({2})", DateTime.Now, displayedString(level), location(caller));
            
            if (message != null)
                outputBuilder.AppendFormat(": {0}", message);
        
            outputBuilder.Append('\n');
        
            if (details != null)
            {
                outputBuilder.Append('\n');
            
                foreach (string line in details.Split('\n'))
                    outputBuilder.AppendFormat("    {0}\n", line);

                outputBuilder.Append('\n');
            }
        
            return outputBuilder.ToString();
        }

        private static string location(StackFrame caller)
        {
            string     fileName = caller.GetFileName();
            MethodBase method   = caller.GetMethod();

            if (fileName == null)
                return string.Format("{0}.{1}", method.DeclaringType.Name, method.Name);
            else
            {
                return string.Format("{0}:{1},{2}",
                    fileName.Substring(fileName.LastIndexOf('/') + 1),
                    method.Name, caller.GetFileLineNumber());
            }
        }
    
        private static string displayedString(Level level)
        {
            switch (level)
            {
                case Level.Debug:   return "[DEBUG]";
                case Level.Error:   return "[ERROR]";
                case Level.Warning: return "[WARN] ";
                case Level.Major:   return "[MAJOR]";
                case Level.Minor:   return "[-----]";
                case Level.Verbose: return "[---]  ";
            }

            throw new Exception("Unreachable");
        }

        private static TextWriter writerFor(Level level)
        {
            switch (level)
            {
                case Level.Debug:   return outputWriter;
                case Level.Error:   return errorWriter;
                case Level.Warning: return errorWriter;
                case Level.Major:   return outputWriter;
                case Level.Minor:   return outputWriter;
                case Level.Verbose: return outputWriter;
            }

            throw new Exception("Unreachable");
        }
    }
}
