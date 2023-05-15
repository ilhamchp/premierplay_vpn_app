package com.cahyahadip.premierplayvpn

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.vpnwholesaler.vpnsdk.VPNSDK
import com.vpnwholesaler.vpnsdk.rest.model.ServerInfo
import org.json.JSONException
import org.json.JSONObject


class MainActivity : AppCompatActivity(), VPNSDK.CommandNotifyCB {
    lateinit var tvTitle: TextView
    lateinit var swVPN: SwitchMaterial
    private var selectedServer: ServerInfo? = null
    private var status: Int = VPNSDK.VPN_NOTIFY_STATUS.VPN_NOTIFY_DISCONNECTED
    private var loggedIn: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvTitle = findViewById(R.id.tvTitle)
        swVPN = findViewById(R.id.swVPN)

        swVPN.setOnClickListener {
            when(status) {
                VPNSDK.VPN_NOTIFY_STATUS.VPN_NOTIFY_DISCONNECTED -> {
                    connectVPN()
                }
                VPNSDK.VPN_NOTIFY_STATUS.VPN_NOTIFY_CONNECTED -> {
                    disconnectVPN()
                }
            }
        }

        swVPN.setOnCheckedChangeListener { _, isChecked ->
            swVPN.isChecked = !isChecked
        }

        initVPNCore()
    }

    private fun initVPNCore() {
        VPNSDK.InitOVSCore(this, this)
        VPNSDK.CmdProc(VPNSDK.OVS_CMD_CODES.OVS_CMD_SET_API_DOMAIN, null, VPN_SUBDOMAIN)
        VPNSDK.CmdProc(VPNSDK.OVS_CMD_CODES.OVS_CMD_SET_API_KEY, null, VPN_API_KEY)
        VPNSDK.CmdProc(VPNSDK.OVS_CMD_CODES.OVS_CMD_SET_LOGGING, null, "enable")
        VPNSDK.CmdProc(VPNSDK.OVS_CMD_CODES.OVS_CMD_GET_STATUS, null)
        VPNSDK.CmdProc(VPNSDK.OVS_CMD_CODES.OVS_CMD_LOGIN,
            { _, error: Int, _ -> onLogin(error) },
            USER_EMAIL,
            USER_PASSWORD
        )
    }

    private fun onLogin(error: Int) {
        if(error == VPNSDK.OVS_ERROR_CODES.OVS_ERR_OK) {
            loggedIn = true
            VPNSDK.CmdProc(VPNSDK.OVS_CMD_CODES.OVS_CMD_GET_SERVLIST_BYCOUNTRY,
                null, VPN_COUNTRY, true)
        } else {
            loggedIn = false
        }
    }

    private fun onServerLoaded(server: LinkedHashMap<String, ServerInfo>?) {
        if (server == null) return
        val serverList: ArrayList<ServerInfo?> = ArrayList()
        for (key in server.keys) {
            serverList.add(server[key])
        }
        // Select server random
        selectedServer = serverList.asSequence().shuffled().find { true }
    }

    private fun connectVPN() {
        if (selectedServer != null) {
            val protocol = VPNSDK.VPN_TYPES.OPENVPN
            VPNSDK.CmdProc(VPNSDK.OVS_CMD_CODES.OVS_CMD_SET_VPN_TYPE,
                { _, _, _ -> doConnect(protocol, selectedServer!!) }, protocol
            )
        }
    }

    private fun doConnect(vpnType: Int, server: ServerInfo) {
        when (vpnType) {
            VPNSDK.VPN_TYPES.OPENVPN -> {
                VPNSDK.CmdProc(
                    VPNSDK.OVS_CMD_CODES.OVS_CMD_CONNECT,
                    null,
                    server.serverIP,
                    "tcp",
                    443,
                    1,
                    1
                )
            }
            VPNSDK.VPN_TYPES.IKEv2 -> {
                cmd(VPNSDK.OVS_CMD_CODES.OVS_CMD_CONNECT, "server_name", server.hostName)
            }
            VPNSDK.VPN_TYPES.SHADOWSOCKS -> {
                cmd(
                    VPNSDK.OVS_CMD_CODES.OVS_CMD_CONNECT,
                    "ip_addr",
                    server.serverIP,
                    "port",
                    443
                )
            }
            VPNSDK.VPN_TYPES.WIREGUARD -> {
                cmd(
                    VPNSDK.OVS_CMD_CODES.OVS_CMD_CONNECT,
                    "ip_addr",
                    server.serverIP,
                    "port",
                    server.wg_port,
                    "wg_pubkey",
                    server.wg_public
                )
            }
        }

    }

    private fun disconnectVPN() {
        VPNSDK.CmdProc(VPNSDK.OVS_CMD_CODES.OVS_CMD_DISCONNECT, null)
    }

    override fun onNotify(notification: Int, error: Int, data: Any?) {
        when (notification) {
            // Following is calledn when VPN server list retrieved
            VPNSDK.OVS_NOTIFY_CODES.OVS_NOTIFY_GET_SERVLIST_BYCOUNTRY -> {
                if (error == VPNSDK.OVS_ERROR_CODES.OVS_ERR_OK) {
                    onServerLoaded(data as LinkedHashMap<String, ServerInfo>?)
                }

            }
            // Following is called when VPN status was changed
            VPNSDK.OVS_NOTIFY_CODES.OVS_NOTIFY_VPN_CONNECT_FAILED,
            VPNSDK.OVS_NOTIFY_CODES.OVS_NOTIFY_VPN_ABNOMARLY_DISCONNECTED,
            VPNSDK.OVS_NOTIFY_CODES.OVS_NOTIFY_VPN_DISCONNECTED -> {
                // Do something when VPN was disconnected
                status = VPNSDK.VPN_NOTIFY_STATUS.VPN_NOTIFY_DISCONNECTED
                onStatusChanged(status)
            }
            VPNSDK.OVS_NOTIFY_CODES.OVS_NOTIFY_VPN_CONNECTING -> {
                // Do something when VPN was started to connect
                status = VPNSDK.VPN_NOTIFY_STATUS.VPN_NOTIFY_CONNECTING
                onStatusChanged(status)
            }
            VPNSDK.OVS_NOTIFY_CODES.OVS_NOTIFY_VPN_CONNECTED -> {
                // Do something when VPN was connected
                status = VPNSDK.VPN_NOTIFY_STATUS.VPN_NOTIFY_CONNECTED
                onStatusChanged(status)
            }
            // Following is called when we request VPN status explicitly

            VPNSDK.OVS_NOTIFY_CODES.OVS_NOTIFY_GET_STATUS -> {
                status = (data as Map<*, *>)["VPN_Status"] as Int
                if (error == VPNSDK.OVS_ERROR_CODES.OVS_ERR_OK) {
                    val status = data["VPN_Status"] as Int?
                    onStatusChanged(status!!)
                } else {
                    onStatusChanged(VPNSDK.VPN_NOTIFY_STATUS.VPN_NOTIFY_DISCONNECTED)
                }

            }
        }
    }

    override fun onDestroy() {
        VPNSDK.FinalizeOVSCore()
        super.onDestroy()
    }

    private fun onStatusChanged(status: Int) {
        when(status) {
            VPNSDK.VPN_NOTIFY_STATUS.VPN_NOTIFY_DISCONNECTED -> {
                swVPN.isEnabled = true
                swVPN.isChecked = false
                swVPN.text = "VPN Off"
            }
            VPNSDK.VPN_NOTIFY_STATUS.VPN_NOTIFY_CONNECTED -> {
                swVPN.isEnabled = true
                swVPN.isChecked = true
                swVPN.text = "VPN On"
            }
            else -> {
                swVPN.isEnabled = false
                swVPN.text = "Connecting..."
            }
        }
    }

    private fun cmd(cmdCode: Int, vararg args: Any) {
        try {
            val data = JSONObject()
            var i = 0
            while (i < args.size) {
                data.put(args[i].toString(), args[i + 1])
                i += 2
            }
            val result = JSONObject()
            result.put("cmd", cmdCode)
            result.put("data", data)
            VPNSDK.CmdProc(result.toString())
        } catch (e: JSONException) {
        }
    }

    companion object {
        const val VPN_SUBDOMAIN: String = "premierplay.vpngn.com"
        const val VPN_API_KEY: String = "T0V4L0XCCW2254BNZBQH2CLMK"
        const val VPN_COUNTRY: String = "SG"
        const val USER_EMAIL: String = "ilhamchp@gmail.com"
        const val USER_PASSWORD: String = "Ilhamchp@123"
    }
}