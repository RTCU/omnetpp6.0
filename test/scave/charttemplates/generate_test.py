#!/usr/bin/env python3

import os
import sys

from omnetpp.scave.analysis import *
from omnetpp.scave.charttemplate import *

TEMPLATE_DIR = "../../../ui/org.omnetpp.scave.templates/charttemplates/"

skeleton = """
# !!!! THIS CHART IS AUTOGENERATED !!!!

import test_exceptions
from omnetpp.scave import chart

print(test_exceptions.yellow("Running test: {}"))
test_exceptions.messages.clear()

expected_exception = {}

try:
{}
    if expected_exception:
        raise RuntimeError("Expected error did not occur")
except chart.ChartScriptError as e:
    test_exceptions.warn(str(e))
    test_exceptions.expect_message(expected_exception)
except (ValueError, SystemExit) as e:
    print("Unexpected exception:", e)
    test_exceptions.expect_message(expected_exception)

print(test_exceptions.green("PASS"))
"""

def indent(s):
    return "\n".join(["    " + l for l in s.splitlines()])

def generate_testcases(template_ids, base_props, tests):
    global templates
    charts = []
    for template_id in template_ids:
        template = templates[template_id]
        i = 0
        for propname, propvalue, errmsg in tests:
            name = template_id + str(i)
            i += 1
            props = base_props.copy()
            if propname:
                props.update({propname: propvalue})

            chart = template.create_chart(name=name, props=props)
            chart.script = skeleton.format(repr(name), repr(errmsg), indent(chart.script))
            charts.append(chart)
    return charts

templates = load_chart_templates()
charts = []

charts += generate_testcases(
    ["barchart_native", "barchart_mpl"],
    {
        "filter": "runattr:experiment =~ PureAlohaExperiment AND name =~ channelUtilization:last",
        "groups": "iaMean",
        "series": "numHosts"
    },
    [
        ("filter", "runattr:experiment =~ PureAlohaExperiment AND type =~ scalar", None),
        ("filter", "runattr:experiment =~ PureAlohaExperiment AND name =~ channel*", None),
        ("filter", "aa bb", "Syntax error"),
        ("filter", "", "Error while querying results: Empty filter expression"),

        ("groups", "iaMean", None),
        ("groups", "numHosts", "Overlap between Group and Series columns"),
        ("groups", "experiment", None),
        ("groups", "name", None),
        ("groups", "aa bb", "No such iteration variable"),
        ("groups", "", "set both the Groups and Series properties"),

        ("series", "iaMean", "Overlap between Group and Series columns"),
        ("series", "numHosts", None),
        ("series", "experiment", None),
        ("series", "name", None),
        ("series", "", "set both the Groups and Series properties"),
    ]
)

charts += generate_testcases(
    ["linechart_native", "linechart_mpl", "linechart_separate_mpl"],
    {
        "filter": "runattr:experiment =~ Fifo*",
    },
    [
        ("filter", "runattr:experiment =~ Fifo* AND type =~ vector", None),
        ("filter", "runattr:experiment =~ Fifo* AND name =~ qlen:vector", None),
        ("filter", "aa bb", "Syntax error"),
        ("filter", "", "Error while querying results: Empty filter expression"),

        ("vector_start_time", "10", None),
        ("vector_end_time", "20", None),

        ("vector_operations", "apply:mean", None),
        ("vector_operations", "compute:sum", None),

        ("vector_operations", "apply:sum\ncompute:divtime\napply:timewinavg(window_size=200) # comment", None)
    ]
)

charts += generate_testcases(
    ["scatterchart_itervars_native", "scatterchart_itervars_mpl"],
    {
        "filter": "runattr:experiment =~ PureAlohaExperiment AND name =~ channelUtilization:last",
        "xaxis_itervar": "iaMean",
        "group_by": "numHosts"
    },
    [
        ("filter", "runattr:experiment =~ PureAlohaExperiment AND type =~ scalar", None),
        ("filter", "runattr:experiment =~ PureAlohaExperiment AND name =~ channel*", None),
        ("filter", "aa bb", "Syntax error"),
        ("filter", "", "Error while querying results: Empty filter expression"),

        ("xaxis_itervar", "iaMean", None),
        ("xaxis_itervar", "numHosts", "X axis column also in grouper columns:"),
        ("xaxis_itervar", "experiment", None),
        ("xaxis_itervar", "name", None),
        ("xaxis_itervar", "aa bb", "iteration variable for the X axis could not be found"),
        ("xaxis_itervar", "", "select the iteration variable for the X axis"),

        ("group_by", "iaMean", "X axis column also in grouper columns:"),
        ("group_by", "numHosts", None),
        ("group_by", "numHosts, replication", None),
        ("group_by", "experiment", None),
        ("group_by", "name", None),
        ("group_by", "aa bb", "iteration variable for grouping could not be found"),
        ("group_by", "", None),
    ]
)

charts += generate_testcases(
    ["histogramchart_native", "histogramchart_mpl"], #TODO "histogramchart_vectors_native", "histogramchart_vectors_mpl"
    {
        "filter": "runattr:experiment =~ PureAlohaExperiment",
    },
    [
        ("filter", "runattr:experiment =~ PureAlohaExperiment AND type =~ histogram AND itervar:numHosts =~ 15 AND (itervar:iaMean =~ 1 OR itervar:iaMean =~ 2)", None),
        ("filter", "runattr:experiment =~ PureAlohaExperiment AND nonexistent", "returned no data"),
        ("filter", "aa bb", "Syntax error"),
        ("filter", "", "Error while querying results: Empty filter expression"),
    ]
)

charts += generate_testcases(
    ["generic_mpl"],
    {
        "input": "Hello",
    },
    [
        ("input", "", None),
        ("input", "FooBar", None),
    ]
)

charts += generate_testcases(
    ["generic_xyplot_native", "generic_xyplot_mpl"],
    {
    },
    [
        (None, None, None),
    ]
)

charts += generate_testcases(
    ["3dchart_itervars_mpl"],
    {
        "filter": "runattr:experiment =~ PureAlohaExperiment AND name =~ channelUtilization:last",
        "xaxis_itervar": "iaMean",
        "yaxis_itervar": "numHosts",
        "colormap": "viridis",
        "colormap_reverse": "false",
        "chart_type": "bar"
    },
    [
        ("filter", "runattr:experiment =~ PureAlohaExperiment AND type =~ scalar", "scalars must share the same name"),
        ("filter", "runattr:experiment =~ PureAlohaExperiment AND name =~ channel*", None),
        ("filter", "aa bb", "Syntax error"),
        ("filter", "", "Error while querying results: Empty filter expression"),

        ("xaxis_itervar", "iaMean", None),
        ("xaxis_itervar", "numHosts", "The itervar for the X and Y axes are the same"),
        ("xaxis_itervar", "aa bb", "iteration variable for the X axis could not be found"),
        ("xaxis_itervar", "", "set both the X Axis and Y Axis options"),

        ("yaxis_itervar", "iaMean", "The itervar for the X and Y axes are the same"),
        ("yaxis_itervar", "numHosts", None),
        ("yaxis_itervar", "aa bb", "iteration variable for the Y axis could not be found"),
        ("yaxis_itervar", "", "set both the X Axis and Y Axis options"),

        ("chart_type", "points", None),
        ("chart_type", "surface", None),
        ("chart_type", "trisurf", None),
    ]
)

charts += generate_testcases(
    ["boxwhiskers"],
    {
        "filter": "runattr:experiment =~ PureAlohaExperiment AND *:histogram"
    },
    [
        ("filter", "runattr:experiment =~ PureAlohaExperiment AND type =~ histogram", None),
        ("filter", "aa bb", "Syntax error"),
        ("filter", "", "Error while querying results: Empty filter expression"),
    ]
)

inputs = [ "/resultfiles/aloha", "/resultfiles/fifo" ]
analysis = Analysis(inputs=inputs, items=charts)

analysis.to_anf_file("all_the_tests.anf")

# print which chart templates are not covered here
tested = set([chart.template for chart in charts])
all = set(templates.keys())
untested = all.difference(tested)
print("Untested chart templates (not covered by this test):", untested)