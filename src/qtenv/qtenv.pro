#-------------------------------------------------
#
# Project created by QtCreator 2015-03-13T00:38:46
#
#-------------------------------------------------
#
# THIS FILE IS NO LONGER USED FOR BUILDING QTENV.
#
# It is merely a "project file" for Qt Creator.
# The following still applies though:
#
# To properly set up the project/build process for Qt Creator you need to invoke only
# the 'make' command in this directory (by default Qt Creator also invokes qmake)
#
# - make sure PATH contains the omnetpp/bin directory
#   (for example by starting Qt Creator from the command line after sourcing the setenv script)
# - open this file as a project
# - select the "Projects" pane on the left
# - press "Configure Project" button
# - select the "Projects" pane again
# - turn off "Shadow build"
# - on the build steps, delete the "qmake" build step
# - optional (if you want to create release builds from QtCreator):
#   for the release configuration add the MODE=release argument to the make line
# - optional (for faster build times):
#   add -j8 (or whatever number suits your system best) to the Make arguments line

TARGET = qtenv
TEMPLATE = lib

MAKEFILE_GENERATOR = UNIX
QMAKE_MACOSX_DEPLOYMENT_TARGET = 10.7

# makes the QMAKE_MOC variable available, so we can add OPP_CFLAGS to it
load(moc)

# IMPORTANT: on turn off the option to generate both debug and release version
# we need only one of them not both and this is the default setting on Windows
# but sadly it generates broken makefiles
CONFIG *= static c++14 qt
CONFIG -= debug_and_release
CONFIG -= warn_on warn_off
DEFINES += "QT_NO_EMIT"
WARNING_FLAGS *= -Wall -Wextra -Wno-unused-parameter -Wno-microsoft-enum-value
QMAKE_CXXFLAGS += $$(OPP_CFLAGS) $$WARNING_FLAGS
QMAKE_CFLAGS += $$(OPP_CFLAGS) $$WARNING_FLAGS
# we have to add all defines to the MOC compiler command line to correctly parse the code
QMAKE_MOC += $$(OPP_DEFINES)

# add QT modules
QT *= core gui opengl widgets printsupport

SOURCES += mainwindow.cc \
    areaselectordialog.cc \
    arrow.cc \
    figurerenderers.cc \
    histograminspector.cc \
    logbuffer.cc \
    outputvectorinspector.cc \
    canvasinspector.cc \
    inspector.cc \
    loginspector.cc \
    qtenv.cc \
    watchinspector.cc \
    canvasrenderer.cc \
    genericobjectinspector.cc \
    inspectorfactory.cc \
    moduleinspector.cc \
    componenthistory.cc \
    layouterenv.cc \
    stopdialog.cc \
    runselectiondialog.cc \
    imagecache.cc \
    treeitemmodel.cc \
    genericobjecttreemodel.cc \
    qtutil.cc \
    inspectorutil.cc \
    textviewerwidget.cc \
    textviewerproviders.cc \
    logfinddialog.cc \
    logfilterdialog.cc \
    timelinegraphicsview.cc \
    timelineinspector.cc \
    preferencesdialog.cc \
    objecttreeinspector.cc \
    osgcanvasinspector.cc \
    rununtildialog.cc \
    submoduleitem.cc \
    modulecanvasviewer.cc \
    comboselectiondialog.cc \
    compoundmoduleitem.cc \
    connectionitem.cc \
    messageitem.cc \
    canvasviewer.cc \
    layersdialog.cc \
    fileeditor.cc \
    animationcontrollerdialog.cc \
    messageanimator.cc \
    displayupdatecontroller.cc \
    messageanimations.cc \
    graphicsitems.cc \
    modulelayouter.cc \
    objectlistmodel.cc \
    objectlistview.cc \
    findobjectsdialog.cc \
    outputvectorinspectorconfigdialog.cc \
    outputvectorview.cc \
    histogramview.cc \
    histograminspectorconfigdialog.cc \
    chartgriditem.cc \
    vectorplotitem.cc \
    charttickdecimal.cc \
    exponentialspinbox.cc \
    genericobjecttreenodes.cc \
    highlighteritemdelegate.cc \
    iosgviewer.cc \
    messageprintertagsdialog.cc \
    videorecordingdialog.cc

HEADERS += mainwindow.h \
    areaselectordialog.h \
    arrow.h \
    componenthistory.h \
    layouterenv.h \
    moduleinspector.h \
    qtenv.h \
    watchinspector.h \
    canvasinspector.h \
    figurerenderers.h \
    histograminspector.h \
    logbuffer.h \
    outputvectorinspector.h \
    canvasrenderer.h \
    inspectorfactory.h \
    loginspector.h \
    circularbuffer.h \
    genericobjectinspector.h \
    inspector.h \
    qtenvdefs.h \
    runselectiondialog.h \
    treeitemmodel.h \
    stopdialog.h \
    imagecache.h \
    genericobjecttreemodel.h \
    qtutil.h \
    inspectorutil.h \
    textviewerwidget.h \
    textviewerproviders.h \
    logfinddialog.h \
    logfilterdialog.h \
    timelinegraphicsview.h \
    timelineinspector.h \
    preferencesdialog.h \
    objecttreeinspector.h \
    osgcanvasinspector.h \
    rununtildialog.h \
    submoduleitem.h \
    modulecanvasviewer.h \
    comboselectiondialog.h \
    compoundmoduleitem.h \
    connectionitem.h \
    messageitem.h \
    canvasviewer.h \
    layersdialog.h \
    fileeditor.h \
    animationcontrollerdialog.h \
    messageanimator.h \
    displayupdatecontroller.h \
    messageanimations.h \
    graphicsitems.h \
    modulelayouter.h \
    objectlistmodel.h \
    objectlistview.h \
    findobjectsdialog.h \
    outputvectorinspectorconfigdialog.h \
    outputvectorview.h \
    histogramview.h \
    inspectorutiltypes.h \
    histograminspectorconfigdialog.h \
    chartgriditem.h \
    vectorplotitem.h \
    charttickdecimal.h \
    exponentialspinbox.h \
    genericobjecttreenodes.h \
    highlighteritemdelegate.h \
    iosgviewer.h \
    messageprintertagsdialog.h \
    videorecordingdialog.h

# include path is relative to the current build directory (e.g. out/src/gcc-debug/qtenv)
INCLUDEPATH += ../../../../src ../../../../include

# next line is for the QtCreator only to be able to show the OMNeT++ sources (not needed for the actual build process)
INCLUDEPATH += .. ../../include

FORMS += mainwindow.ui \
    areaselectordialog.ui \
    runselectiondialog.ui \
    stopdialog.ui \
    logfinddialog.ui \
    logfilterdialog.ui \
    preferencesdialog.ui \
    rununtildialog.ui \
    comboselectiondialog.ui \
    layersdialog.ui \
    fileeditor.ui \
    animationcontrollerdialog.ui \
    findobjectsdialog.ui \
    outputvectorinspectorconfigdialog.ui \
    histograminspectorconfigdialog.ui \
    messageprintertagsdialog.ui \
    videorecordingdialog.ui

RESOURCES += \
    fonts.qrc \
    icons.qrc
