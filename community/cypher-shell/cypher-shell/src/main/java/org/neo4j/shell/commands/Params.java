/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.shell.commands;

import static org.neo4j.shell.prettyprint.CypherVariablesFormatter.escape;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.neo4j.shell.exception.CommandException;
import org.neo4j.shell.exception.ExitException;
import org.neo4j.shell.parameter.ParameterService;
import org.neo4j.shell.prettyprint.CypherVariablesFormatter;
import org.neo4j.shell.printer.Printer;

/**
 * This lists all query parameters which have been set
 */
public class Params implements Command {
    private static final Pattern backtickPattern = Pattern.compile("^\\s*(?<key>(`([^`])*`)+?)\\s*");
    private final Printer printer;
    private final ParameterService parameters;

    public Params(Printer printer, ParameterService parameters) {
        this.printer = printer;
        this.parameters = parameters;
    }

    @Override
    public void execute(final List<String> args) throws ExitException, CommandException {
        requireArgumentCount(args, 0, 1);

        if (args.size() == 0) {
            listAllParams();
        }
        if (args.size() == 1) {
            String trim = args.get(0).trim();
            Matcher matcher = backtickPattern.matcher(trim);
            if (trim.startsWith("`") && matcher.matches()) {
                listParam(trim);
            } else {
                String[] slittedArgs = trim.split("\\s+");
                if (slittedArgs.length > 0) {
                    listParam(slittedArgs[0]);
                } else {
                    listAllParams();
                }
            }
        }
    }

    private void listParam(String name) throws CommandException {
        String parameterName = CypherVariablesFormatter.unescapedCypherVariable(name);
        if (!parameters.parameters().containsKey(parameterName)) {
            throw new CommandException("Unknown parameter: " + name);
        }
        listParam(
                name.length(), name, parameters.parameters().get(parameterName).expressionString());
    }

    private void listParam(int leftColWidth, String key, Object value) {
        printer.printOut(String.format(":param %-" + leftColWidth + "s => %s", key, value));
    }

    private void listAllParams() {
        List<String> keys = parameters.parameters().keySet().stream().sorted().collect(Collectors.toList());

        int leftColWidth = keys.stream().map(s -> escape(s).length()).reduce(0, Math::max);

        keys.forEach(key -> listParam(
                leftColWidth, escape(key), parameters.parameters().get(key).expressionString()));
    }

    public static class Factory implements Command.Factory {
        @Override
        public Metadata metadata() {
            var help = "Print a table of all currently set query parameters or the value for the given parameter";
            var description = "Print all query parameter values";
            return new Metadata(":params", description, "[parameter]", help, List.of("parameters"));
        }

        @Override
        public Command executor(Arguments args) {
            return new Params(args.printer(), args.parameters());
        }
    }
}
