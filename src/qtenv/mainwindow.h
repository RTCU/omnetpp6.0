#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include "qtenvdefs.h"
#include <QMainWindow>
#include <QModelIndex>

#include <QDebug>

class QGraphicsScene;
class QStandardItem;
class QTreeView;
class cObject;
class cEvent;

NAMESPACE_BEGIN
class cCanvas;
class cMessage;
namespace qtenv {
class Qtenv;
class Inspector;
}

NAMESPACE_END

namespace Ui {
class MainWindow;
}

class MainWindow : public QMainWindow
{
    Q_OBJECT

public:
    QGraphicsScene *scene;

    explicit MainWindow(qtenv::Qtenv *env, QWidget *parent = 0);

    void displayText(const char* t);
    QTreeView *getObjectTree();
    void updateStatusDisplay();
    void updateNetworkRunDisplay();
    void redrawTimeline();

    ~MainWindow();

private slots:
    void on_actionOneStep_triggered();
    void on_actionQuit_triggered();
    void on_actionRun_triggered();
    void on_actionSetUpConfiguration_triggered();
    void on_actionStop_triggered();
    void on_actionFastRun_triggered();
    void on_actionExpressRun_triggered();
    void on_actionRunUntil_triggered();
    void inspectObject(QModelIndex index);
    void onTreeViewContextMenu(QPoint point);
    void on_actionRebuildNetwork_triggered();

public slots:
    void onClickOpenInspector();
    void onClickRun();
    void onClickRunMessage();
    void onClickExcludeMessage();
private:
    enum Mode { STEP, NORMAL, FAST, EXPRESS, NOT_RUNNING};
    Ui::MainWindow *ui;
    qtenv::Qtenv *env;

    bool isRunning();
    bool checkRunning();
    void setGuiForRunmode(Mode mode, qtenv::Inspector *insp = nullptr, bool untilMode = false);
    void runSimulation(Mode mode);

    void updateSimtimeDisplay();
    void updatePerformanceDisplay();
    void updateNextModuleDisplay();
    int getObjectId(cEvent *object);
    const char *getObjectShortTypeName(cObject *object);
    const char *stripNamespace(const char *className);

    void inspectObject(cObject *object, int type = 0, const char *geometry = "");
    void runSimulationLocal(qtenv::Inspector *insp, int runMode, cObject *object = nullptr);
    void setRunUntilModule(qtenv::Inspector *insp);
    int modeToRunMode(Mode mode);
    Mode runModeToMode(int runMode);

    void runUntilMsg(cMessage *msg, int runMode);
    void excludeMessageFromAnimation(cObject *msg);

    bool networkReady();
    bool networkPresent();
    bool isSimulationOk();

    void busy(QString msg = "");
};

#endif // MAINWINDOW_H
