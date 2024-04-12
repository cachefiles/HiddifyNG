package com.v2ray.ang.ui

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.*
import android.net.Uri
import android.net.VpnService
import androidx.recyclerview.widget.LinearLayoutManager
import com.tbruyelle.rxpermissions.RxPermissions
import com.v2ray.ang.R
import android.os.Bundle
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import android.content.res.ColorStateList
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.hiddify.ang.BaseFragment
import com.tapadoo.alerter.Alerter
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.click
import com.v2ray.ang.extension.toast
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.ui.bottomsheets.AddConfigBottomSheets
import com.v2ray.ang.ui.bottomsheets.BottomSheetPresenter
import com.v2ray.ang.ui.bottomsheets.ProfilesBottomSheets
import com.v2ray.ang.util.*
import com.v2ray.ang.viewmodel.HiddifyMainViewModel
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.*
import me.drakeet.support.toast.ToastCompat

class MainActivity(val mainViewModel: HiddifyMainViewModel) : BaseFragment(){
    private lateinit var binding: ActivityMainBinding
    private val bottomSheetPresenter = BottomSheetPresenter()
//    private var subAdapter by lazy {  }//hiddify
    private val adapter by lazy { MainRecyclerAdapter(requireActivity(),mainViewModel) }
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private var mItemTouchHelper: ItemTouchHelper? = null


    //Hiddify
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            importConfigViaSub(HiddifyUtils.getSelectedSubId())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        super.onCreateView(inflater, container, savedInstanceState)

//        (requireActivity() as BaseActivity).supportActionBar?.subtitle="Dd"
        activity?.registerReceiver(receiver, IntentFilter(AppConfig.BROADCAST_ACTION_UPDATE_UI))//hiddify
        binding = ActivityMainBinding.inflate(layoutInflater)
        mainViewModel.subscriptionId.observe(this){
            onSelectSub(it,true)
        }
        val view = binding.root
//        setContentView(view)
//        title = getString(R.string.title_server)
//        setSupportActionBar(binding.toolbar)
        binding.selectedSubNew.click {
            bottomSheetPresenter.show(parentFragmentManager,ProfilesBottomSheets.newInstance())
        }
        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(requireActivity())
            } else if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
                val intent = VpnService.prepare(requireActivity())
                if (intent == null) {
                    startV2Ray()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
            }
        }
        binding.layoutTest.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            } else {
//                tv_test_state.text = getString(R.string.connection_test_fail)
            }
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireActivity())
        binding.recyclerView.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)


//        val toggle = ActionBarDrawerToggle(
//                this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
//        binding.drawerLayout.addDrawerListener(toggle)
//        toggle.syncState()
//        binding.navView.setNavigationItemSelectedListener(this)
//        "v${BuildConfig.VERSION_NAME} (${SpeedtestUtil.getLibVersion()})".also { binding.version.text = it }

        setupViewModel()
//        copyAssets()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RxPermissions(requireActivity())
                .request(Manifest.permission.POST_NOTIFICATIONS)
                .subscribe {
//                    if (!it)
//                        requireActivity().toast(R.string.toast_permission_denied)
                }
        }

        //hiddify


            //hiddify}
        return binding.root
    }


    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (isRunning) {
//                if (!Utils.getDarkModeStatus(context!!)) {
//                    binding.fab.setImageResource(R.drawable.ic_stat_name)
//                }
                binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.color_fab_orange))
                setTestState(getString(R.string.connection_connected))
                binding.layoutTest.isFocusable = true
                binding.fab.text=getString(R.string.fab_connected) //hiddify
                setTestState(getString(R.string.connection_test_testing)) //hiddify
                mainViewModel.testCurrentServerRealPing()//hiddify
            } else {
//                if (!Utils.getDarkModeStatus(requireContext())) {
//                    binding.fab.setImageResource(R.drawable.ic_stat_name)
//                }
                binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.color_fab_grey))
                setTestState(getString(R.string.connection_not_connected))
                binding.layoutTest.isFocusable = false
                binding.fab.text=getString(R.string.fab_start)//hiddify
            }
            hideCircle()
        }
//        mainViewModel.startListenBroadcast()
    }





    fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        showCircle()
//        requireActivity().toast(R.string.toast_services_start)
        V2RayServiceManager.startV2Ray(requireActivity())
        hideCircle()
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            Utils.stopVService(requireActivity())
        }
        Observable.timer(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                startV2Ray()
            }
    }

    public override fun onResume() {
        super.onResume()
//        mainViewModel.reloadServerList()

        onSelectSub(HiddifyUtils.getSelectedSubId(),false)
//        if (V2RayServiceManager.v2rayPoint.isRunning) {
//            mainViewModel.testCurrentServerRealPing()
//        }
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
    }
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        requireActivity().menuInflater.inflate(R.menu.menu_main, menu)
    }


    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode(true)
            true
        }
        R.id.import_clipboard -> {
            importClipboard()
            importConfigViaSub(HiddifyUtils.getSelectedSubId())
            true
        }
        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }
        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }
        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }
        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }
        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }
        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value)
            true
        }
        R.id.import_config_custom_clipboard -> {
            importConfigCustomClipboard()
            true
        }
        R.id.import_config_custom_local -> {
            importConfigCustomLocal()
            true
        }
        R.id.import_config_custom_url -> {
            importConfigCustomUrlClipboard()
            true
        }
        R.id.import_config_custom_url_scan -> {
            importQRcode(false)
            true
        }

//        R.id.sub_setting -> {
//            startActivity<SubSettingActivity>()
//            true
//        }

        R.id.sub_update -> {
            var selected=HiddifyUtils.getSelectedSub()
            if (Utils.isValidUrl(selected?.second?.url))
                importConfigViaSub(HiddifyUtils.getSelectedSubId())
            else
                mainViewModel.testAllRealPing()
            true
        }

        R.id.export_all -> {
            if (AngConfigManager.shareNonCustomConfigsToClipboard(requireActivity(), mainViewModel.serverList) == 0) {
                requireActivity().toast(R.string.toast_success)
            } else {
                requireActivity().toast(R.string.toast_failure)
            }
            true
        }

        R.id.ping_all -> {
            mainViewModel.testAllTcping()
            true
        }

        R.id.real_ping_all -> {
            mainViewModel.testAllRealPing()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            AlertDialog.Builder(requireActivity()).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
//                    MmkvManager.removeAllServer()
                    MmkvManager.removeSubscription(HiddifyUtils.getSelectedSubId())
                    HiddifyUtils.setSelectedSub("default")
                    mainViewModel.reloadServerList()
                }
                .show()
            true
        }
        R.id.del_duplicate_config-> {
            AlertDialog.Builder(requireActivity()).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    mainViewModel.removeDuplicateServer()
                }
                .show()
            true
        }
        R.id.del_invalid_config -> {
            AlertDialog.Builder(requireActivity()).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeInvalidServer()
                    mainViewModel.reloadServerList()
                }
                .show()
            true
        }
        R.id.sort_by_test_results -> {
            MmkvManager.sortByTestResults()
            mainViewModel.reloadServerList()
            true
        }
        R.id.filter_config -> {
            mainViewModel.filterConfig(requireActivity())
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType : Int) {
        startActivity(
            Intent()
                .putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", mainViewModel.subscriptionId.value)
                .setClass(requireActivity(), ServerActivity::class.java)
        )
    }

    /**
     * import config from qrcode
     */
    fun importQRcode(forConfig: Boolean): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(requireActivity())
            .request(Manifest.permission.CAMERA)
            .subscribe {
                if (it)
                    if (forConfig)
                        scanQRCodeForConfig.launch(Intent(requireActivity(), ScannerActivity::class.java))
                    else
                        scanQRCodeForUrlToCustomConfig.launch(Intent(requireActivity(), ScannerActivity::class.java))
                else
                    requireActivity().toast(R.string.toast_permission_denied)
            }
//        }
        return true
    }

    private val scanQRCodeForConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"), selectSub = true)
        }
    }

    private val scanQRCodeForUrlToCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importConfigCustomUrl(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    /**
     * import config from clipboard
     */
    fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(requireActivity())
            importBatchConfig(clipboard, selectSub = true)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importBatchConfig(server: String?, subid: String = "",selectSub:Boolean=false) {//hiddify
        return importBatchConfig(Utils.Response(null, server),subid,selectSub)//hiddify
    }//hiddify
    fun importBatchConfig(response: Utils.Response?, subid: String = "", selectSub:Boolean=false) {//hiddify
        var server=response?.content//hiddify
        val subid2 = if(subid.isNullOrEmpty()){
            HiddifyUtils.getSelectedSubId()
        }else{
            subid
        }
        val append = subid.isNullOrEmpty() || subid=="default"
        HiddifyUtils.extract_package_info_from_response(response,subid)
        var count = AngConfigManager.importBatchConfig(server, subid2, append, selectSub = selectSub)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server!!), subid2, append, selectSub = selectSub)
        }
        if (count > 0) {
//            requireActivity().toast(R.string.toast_success)
            mainViewModel.reloadServerList()
            mainViewModel.testAllRealPing()
        } else {
            requireActivity().toast(R.string.toast_failure)
        }
    }

    fun importConfigCustomClipboard()
            : Boolean {
        try {
            val configText = Utils.getClipboard(requireActivity())
            if (TextUtils.isEmpty(configText)) {
                requireActivity().toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from local config file
     */
    fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importConfigCustomUrlClipboard()
            : Boolean {
        try {
            val url = Utils.getClipboard(requireActivity())
            if (TextUtils.isEmpty(url)) {
                requireActivity().toast(R.string.toast_none_data_clipboard)
                return false
            }
            return importConfigCustomUrl(url)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from url
     */
    fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                requireActivity().toast(R.string.toast_invalid_url)
                return false
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val configText = try {
                    Utils.getUrlContentWithCustomUserAgent(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
                launch(Dispatchers.Main) {
                    importCustomizeConfig(configText.toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    fun importConfigViaSub(subid: String?=null) : Boolean {
        try {
//            binding.spSubscriptionId.adapter = MainSubAdapter(requireActivity()) //hiddify
            requireActivity().toast(R.string.title_sub_update)
            MmkvManager.decodeSubscriptions().forEach {
                if (subid!=null&&it.first!=subid)return@forEach
                if (TextUtils.isEmpty(it.first)
                    || TextUtils.isEmpty(it.second.remarks)
                    || TextUtils.isEmpty(it.second.url)
                ) {
                    return@forEach
                }
                if (!it.second.enabled) {
                    return@forEach
                }
                val url = Utils.idnToASCII(it.second.url)
                if (!Utils.isValidUrl(url)) {
                    return@forEach
                }
                Log.d(ANG_PACKAGE, url)
                lifecycleScope.launch(Dispatchers.IO) {
                    val configText = try {
                        Utils.getUrlContentWithCustomUserAgent(url)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            requireActivity().toast("\"" + it.second.remarks + "\" " + getString(R.string.toast_failure))
                        }
                        return@launch
                    }
                    launch(Dispatchers.Main) {
                        importBatchConfig(configText, it.first,false)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mainViewModel.testAllRealPing()
            return false
        }

        return true
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            chooseFileForCustomConfig.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: ActivityNotFoundException) {
            requireActivity().toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFileForCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        RxPermissions(requireActivity())
            .request(Manifest.permission.READ_EXTERNAL_STORAGE)
            .subscribe {
                if (it) {
                    try {
                        requireActivity().contentResolver.openInputStream(uri).use { input ->
                            importCustomizeConfig(input?.bufferedReader()?.readText())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else
                    requireActivity().toast(R.string.toast_permission_denied)
            }

    }

    /**
     * import customize config
     */
    fun importCustomizeConfig(server: String?) {
        return importCustomizeConfig(Utils.Response(null, server))//hiddify
    }
    fun importCustomizeConfig(response: Utils.Response) {
        var server=response?.content
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                requireActivity().toast(R.string.toast_none_data)
                return
            }
            mainViewModel.appendCustomConfigServer(response)
            mainViewModel.reloadServerList()
            requireActivity().toast(R.string.toast_success)
            //adapter.notifyItemInserted(mainViewModel.serverList.lastIndex)
        } catch (e: Exception) {
            ToastCompat.makeText(requireActivity(), "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }
    }

    fun setTestState(content: String) {
        setTestState(Pair(0,content))
    }
    fun setTestState(content: Pair<Long,String>?) {
        binding.tvTestState.text = content?.second
    }

//    val mConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName?) {
//        }
//
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
//        }
//    }




    fun showCircle() {
//        binding.fabProgressCircle.show()//hiddify commented
    }

    fun hideCircle() {
//        try { //hiddify commented
//            Observable.timer(300, TimeUnit.MILLISECONDS)
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe {
//                    try {
//                        if (binding.fabProgressCircle.isShown) {
//                            binding.fabProgressCircle.hide()
//                        }
//                    } catch (e: Exception) {
//                        Log.w(ANG_PACKAGE, e)
//                    }
//                }
//        } catch (e: Exception) {
//            Log.d(ANG_PACKAGE, e.toString())
//        }
    }

//    @Deprecated("Deprecated in Java")
//    override fun onBackPressed() {
//        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
//            binding.drawerLayout.closeDrawer(GravityCompat.START)
//        } else {
//            //super.onBackPressed()
////            onBackPressedDispatcher.onBackPressed()
//            finish()
//        }
//
//    }



    fun onSelectSub(subid: String,do_ping:Boolean=true){

//        if (mainViewModel.subscriptionId.value!=subid) {
//            mainViewModel.subscriptionId.value = subid
//            HiddifyUtils.setSelectedSub(mainViewModel.subscriptionId.value)
//        }

//        binding.selectedSubNew.text=
        if(isResumed)
            (requireActivity() as BaseActivity).supportActionBar?.title=(HiddifyUtils.getSelectedSub()?.second?.remarks)

//        mainViewModel.reloadServerList()
//        val selected=HiddifyUtils.getSelectedSub()
//        if (selected?.second?.needUpdate() == true){
//            importConfigViaSub(selected.first)
//        }else
            if (do_ping){
                mainViewModel.testAllRealPing()
        }


    }


//    override fun onAddProfile() {
//        importClipboard()
//        importConfigViaSub(HiddifyUtils.getSelectedSubId())
//    }
//
//    override fun onImportQrCode() {
//        importQRcode(true)
//    }
//
//    override fun onSelectSub(subid: String) {
//        onSelectSub(subid,true)
//    }
//
//    override fun onRemoveSelectSub(subid: String) {
//        if (subid=="default")return
//
//    }
//
//    override fun onUpdateSubList() {
//
//    }
    override fun onTitleClick(){
    bottomSheetPresenter.show(parentFragmentManager,ProfilesBottomSheets.newInstance())
    }

}