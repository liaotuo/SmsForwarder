package com.idormy.sms.forwarder.fragment.senders

import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.fragment.app.viewModels
import com.google.gson.Gson
import com.idormy.sms.forwarder.R
import com.idormy.sms.forwarder.core.BaseFragment
import com.idormy.sms.forwarder.database.AppDatabase
import com.idormy.sms.forwarder.database.entity.Sender
import com.idormy.sms.forwarder.database.viewmodel.BaseViewModelFactory
import com.idormy.sms.forwarder.database.viewmodel.SenderViewModel
import com.idormy.sms.forwarder.databinding.FragmentSendersTelegramBinding
import com.idormy.sms.forwarder.entity.MsgInfo
import com.idormy.sms.forwarder.entity.setting.TelegramSetting
import com.idormy.sms.forwarder.utils.KEY_SENDER_CLONE
import com.idormy.sms.forwarder.utils.KEY_SENDER_ID
import com.idormy.sms.forwarder.utils.KEY_SENDER_TYPE
import com.idormy.sms.forwarder.utils.XToastUtils
import com.idormy.sms.forwarder.utils.sender.TelegramUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.net.Proxy
import java.util.*

@Page(name = "Telegram机器人")
@Suppress("PrivatePropertyName")
class TelegramFragment : BaseFragment<FragmentSendersTelegramBinding?>(), View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private val TAG: String = TelegramFragment::class.java.simpleName
    var titleBar: TitleBar? = null
    private val viewModel by viewModels<SenderViewModel> { BaseViewModelFactory(context) }

    @JvmField
    @AutoWired(name = KEY_SENDER_ID)
    var senderId: Long = 0

    @JvmField
    @AutoWired(name = KEY_SENDER_TYPE)
    var senderType: Int = 0

    @JvmField
    @AutoWired(name = KEY_SENDER_CLONE)
    var isClone: Boolean = false

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSendersTelegramBinding {
        return FragmentSendersTelegramBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.telegram)
        return titleBar
    }

    /**
     * 初始化控件
     */
    override fun initViews() {
        //新增
        if (senderId <= 0) {
            titleBar?.setSubTitle(getString(R.string.add_sender))
            binding!!.btnDel.setText(R.string.discard)
            return
        }

        //编辑
        binding!!.btnDel.setText(R.string.del)
        AppDatabase.getInstance(requireContext())
            .senderDao()
            .get(senderId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : SingleObserver<Sender> {
                override fun onSubscribe(d: Disposable) {}

                override fun onError(e: Throwable) {
                    e.printStackTrace()
                }

                override fun onSuccess(sender: Sender) {
                    if (isClone) {
                        titleBar?.setSubTitle(getString(R.string.clone_sender) + ": " + sender.name)
                        binding!!.btnDel.setText(R.string.discard)
                    } else {
                        titleBar?.setSubTitle(getString(R.string.edit_sender) + ": " + sender.name)
                    }
                    binding!!.etName.setText(sender.name)
                    binding!!.sbEnable.isChecked = sender.status == 1
                    val settingVo = Gson().fromJson(sender.jsonSetting, TelegramSetting::class.java)
                    Log.d(TAG, settingVo.toString())
                    if (settingVo != null) {
                        binding!!.rgMethod.check(settingVo.getMethodCheckId())
                        binding!!.etApiToken.setText(settingVo.apiToken)
                        binding!!.etChatId.setText(settingVo.chatId)
                        binding!!.rgProxyType.check(settingVo.getProxyTypeCheckId())
                        binding!!.etProxyHost.setText(settingVo.proxyHost)
                        binding!!.etProxyPort.setText(settingVo.proxyPort)
                        binding!!.sbProxyAuthenticator.isChecked = settingVo.proxyAuthenticator == true
                        binding!!.etProxyUsername.setText(settingVo.proxyUsername)
                        binding!!.etProxyPassword.setText(settingVo.proxyPassword)
                    }
                }
            })
    }

    override fun initListeners() {
        binding!!.btnTest.setOnClickListener(this)
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
        binding!!.sbProxyAuthenticator.setOnCheckedChangeListener(this)
        binding!!.rgProxyType.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            if (checkedId == R.id.rb_proxyHttp || checkedId == R.id.rb_proxySocks) {
                binding!!.layoutProxyHost.visibility = View.VISIBLE
                binding!!.layoutProxyPort.visibility = View.VISIBLE
                binding!!.layoutProxyAuthenticator.visibility = if (binding!!.sbProxyAuthenticator.isChecked) View.VISIBLE else View.GONE
            } else {
                binding!!.layoutProxyHost.visibility = View.GONE
                binding!!.layoutProxyPort.visibility = View.GONE
                binding!!.layoutProxyAuthenticator.visibility = View.GONE
            }
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        //TODO: 这里只有一个监听不需要判断id
        binding!!.layoutProxyAuthenticator.visibility = if (isChecked) View.VISIBLE else View.GONE
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.btn_test -> {
                    val settingVo = checkSetting()
                    Log.d(TAG, settingVo.toString())
                    val msgInfo = MsgInfo("sms", getString(R.string.test_phone_num), getString(R.string.test_sender_sms), Date(), getString(R.string.test_sim_info))
                    TelegramUtils.sendMsg(settingVo, msgInfo)
                    return
                }
                R.id.btn_del -> {
                    if (senderId <= 0 || isClone) {
                        popToBack()
                        return
                    }

                    MaterialDialog.Builder(requireContext())
                        .title(R.string.delete_sender_title)
                        .content(R.string.delete_sender_tips)
                        .positiveText(R.string.lab_yes)
                        .negativeText(R.string.lab_no)
                        .onPositive { _: MaterialDialog?, _: DialogAction? ->
                            viewModel.delete(senderId)
                            XToastUtils.success(R.string.delete_sender_toast)
                            popToBack()
                        }
                        .show()
                    return
                }
                R.id.btn_save -> {
                    val name = binding!!.etName.text.toString().trim()
                    if (TextUtils.isEmpty(name)) {
                        throw Exception(getString(R.string.invalid_name))
                    }

                    val status = if (binding!!.sbEnable.isChecked) 1 else 0
                    val settingVo = checkSetting()
                    if (isClone) senderId = 0
                    val senderNew = Sender(senderId, senderType, name, Gson().toJson(settingVo), status)
                    Log.d(TAG, senderNew.toString())

                    viewModel.insertOrUpdate(senderNew)
                    XToastUtils.success(R.string.tipSaveSuccess)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString())
            e.printStackTrace()
        }
    }

    private fun checkSetting(): TelegramSetting {
        val apiToken = binding!!.etApiToken.text.toString().trim()
        val chatId = binding!!.etChatId.text.toString().trim()
        if (TextUtils.isEmpty(apiToken) || TextUtils.isEmpty(chatId)) {
            throw Exception(getString(R.string.invalid_apiToken_or_chatId))
        }

        val proxyType: Proxy.Type = when (binding!!.rgProxyType.checkedRadioButtonId) {
            R.id.rb_proxyHttp -> Proxy.Type.HTTP
            R.id.rb_proxySocks -> Proxy.Type.SOCKS
            else -> Proxy.Type.DIRECT
        }
        val proxyHost = binding!!.etProxyHost.text.toString().trim()
        val proxyPort = binding!!.etProxyPort.text.toString().trim()

        if (proxyType != Proxy.Type.DIRECT && (TextUtils.isEmpty(proxyHost) || TextUtils.isEmpty(proxyPort))) {
            throw Exception(getString(R.string.invalid_host_or_port))
        }

        val proxyAuthenticator = binding!!.sbProxyAuthenticator.isChecked
        val proxyUsername = binding!!.etProxyUsername.text.toString().trim()
        val proxyPassword = binding!!.etProxyPassword.text.toString().trim()
        if (proxyAuthenticator && TextUtils.isEmpty(proxyUsername) && TextUtils.isEmpty(proxyPassword)) {
            throw Exception(getString(R.string.invalid_username_or_password))
        }

        val method = if (binding!!.rgMethod.checkedRadioButtonId == R.id.rb_method_get) "GET" else "POST"

        return TelegramSetting(method, apiToken, chatId, proxyType, proxyHost, proxyPort, proxyAuthenticator, proxyUsername, proxyPassword)
    }

}