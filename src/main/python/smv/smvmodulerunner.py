#
# This file is licensed under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from smv.modulesvisitor import ModulesVisitor
from smv.smviostrategy import SmvJsonOnHdfsIoStrategy
from smv.smvmetadata import SmvMetaHistory

class SmvModuleRunner(object):
    """Represent the run-transaction. Provides the single entry point to run
        a group of modules
    """
    def __init__(self, modules, smvApp):
        self.roots = modules
        self.smvApp = smvApp
        self.log = smvApp.log
        self.visitor = ModulesVisitor(modules)

    def run(self):
        # a set of modules which need to run post_action, keep tracking 
        # to make sure post_action run one and only one time for each TX
        # the set will be updated by _create_df, _create_meta and _force_post
        # and eventually be emptied out
        # See docs/dev/SmvGenericModule/SmvModuleRunner.md for details
        mods_to_run_post_action = set(self.visitor.queue)

        # a map from urn to already run DF, since the `run` interface of 
        # SmvModule takes a map of class => df, the map here have to be 
        # keyed by class method instead of `versioned_fqn`, which is only 
        # in the resolved instance
        known = {}

        self._create_df(known, mods_to_run_post_action)

        self._create_meta(mods_to_run_post_action)

        self._force_post(mods_to_run_post_action)

        self._persist_meta()

        return [self._get_df_and_run_info(m) for m in self.roots]

    def publish(self):
        self.run()
        for m in self.roots:
            m.publish()
    
    def purge_persisted(self):
        def cleaner(m, state):
            m.persistStrategy().remove()
            m.metaStrategy().remove()
        self.visitor.dfs_visit(cleaner, None)

    def purge_old_but_keep_new_persisted(self):
        keep = []
        def get_to_keep(m, k):
            k.extend(m.persistStrategy().allOutput())
            k.extend(m.metaStrategy().allOutput())

        self.visitor.dfs_visit(get_to_keep, keep)
        outdir = self.smvApp.all_data_dirs().outputDir

        relative_paths = [i[len(outdir)+1:] for i in keep]

        self.smvApp._jvm.SmvPythonHelper.purgeDirectory(
            outdir,
            relative_paths
        )
        
    def _create_df(self, known, need_post):
        # run module and create df. when persisting, post_action 
        # will run on current module and all upstream modules
        def runner(m, state):
            (urn2df, run_set) = state
            m.rdd(urn2df, run_set)
        self.visitor.dfs_visit(runner, (known, need_post))

    def _create_meta(self, need_post):
        # run user meta. when there is actions in user meta creation,
        # post_action will run on current module and all upstream modules
        def run_meta(m, run_set):
            m.calculate_user_meta(run_set)
        self.visitor.dfs_visit(run_meta, need_post)

    def _force_post(self, need_post):
        # If there are still module left for post_action, force a run here
        # to run them and all left over on their upstream
        if (len(need_post) > 0):
            self.log.debug("leftover mods need to run post action: {}".format(
                [m.fqn()  for m in need_post]
            ))
            def force_run(mod, run_set):
                mod.force_post_action(run_set)
            # Note: we used bfs_visit here run downstream first
            # In case of A<-B<-C all need to run, this way will only
            # need to force action on C, and A and B's post action can 
            # also be calculated
            self.visitor.bfs_visit(force_run, need_post)

    def hist_path(self, m):
        hist_dir = self.smvApp.all_data_dirs().historyDir
        return "{}/{}.hist".format(hist_dir, m.fqn())

    def _persist_meta(self):
        def write_meta(m, state):
            persisted = m.persist_meta()
            if (not persisted):
                io_strategy = SmvJsonOnHdfsIoStrategy(self.smvApp, self.hist_path(m))
                try:
                    hist_json = io_strategy.read()
                    hist = SmvMetaHistory().fromJson(hist_json)
                except:
                    hist = SmvMetaHistory()
                hist.update(m.module_meta, m.metadataHistorySize())
                io_strategy.write(hist.toJson())

        self.visitor.dfs_visit(write_meta, None)

    def _get_df_and_run_info(self, m):
        df = m.df
        meta = m.module_meta
        meta_hist = SmvJsonOnHdfsIoStrategy(self.smvApp, self.hist_path(m)).read()
        return (df, (meta, meta_hist))
