package com.example.bluetoothapp
//link de informacion https://www.youtube.com/watch?v=PtN6UTIu7yw&list=RDCMUCT132T980-1lhm0hcZFy4ZA&index=1
//https://www.youtube.com/watch?v=eg-t_rhDSoM
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    //variables
    private lateinit  var bton: Button
    private lateinit var listBlue: ListView
    //Bluetooth adapter
    private lateinit var blAdapter:BluetoothAdapter
    private lateinit var m_pairedDevices: Set<BluetoothDevice>
    private val REQUEST_ENABLE_BLUETOOTH = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        instancia()
        //inicializa el bluetooth adapter
        blAdapter = BluetoothAdapter.getDefaultAdapter()

        //Verificar si el bluetooth esxiste o no
        if(blAdapter == null){
            Toast.makeText(this,"Este dispositivo no soporta Bluetooth",Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(this,"Bluetooth existe en este celular",Toast.LENGTH_SHORT).show()
        }

        if(!blAdapter!!.isEnabled){
            //se enciende el bluetooth si se encuentra apagado
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent,REQUEST_ENABLE_BLUETOOTH)
        }
        bton.setOnClickListener(){
            listDispositivosEmparejados()
        }
        //Para la cámara ---------------------------------
        cheqPermisos()

    }

    private fun cheqPermisos(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            //Esta condicion entra si es que el permiso no ha sido aceptado por el momento
            solicitaPermisoCmr()

        }else{
            //si acepta el permiso abre camara
            abreCamara()
        }
    }

    private fun solicitaPermisoCmr() {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)){
            //El usuario ya rechazo los permisos y no se volvera a mostrar nada toca ir a la configuracion de la aplicacion y desactivar to eliminartodo el cache
            Toast.makeText(this, "Permisos Rechazados", Toast.LENGTH_SHORT).show()
        }else{
            //aqui se piden los permisos
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 777)
        }
    }
    private fun abreCamara() {
        Toast.makeText(this,"PRESIONE EL DISPOSITIVO BLUETOOTH", Toast.LENGTH_SHORT).show()
    }
    private fun instancia(){
        listBlue = findViewById(R.id.selec_device_list)
        bton = findViewById(R.id.Btn_refresh)
    }

    private fun listDispositivosEmparejados(){
        m_pairedDevices = blAdapter!!.bondedDevices
        val list: ArrayList<BluetoothDevice> = ArrayList()
        if(!m_pairedDevices.isEmpty()){
            for(device: BluetoothDevice in m_pairedDevices){
                val devicename = device.name
                val deviceadrrer = device.address
                list.add(device)
                Log.i("DEVICE"," "+devicename+"\n"+deviceadrrer)
            }
        }else{
            Toast.makeText(this,"No se encuentran dispositivos emparejados",Toast.LENGTH_SHORT).show()
        }
        val adapter = ArrayAdapter(this,android.R.layout.simple_list_item_1,list)
        listBlue.adapter = adapter
        listBlue.onItemClickListener = AdapterView.OnItemClickListener{ _, _, position, _->
            val dev: BluetoothDevice = list[position]
            val address: String = dev.address

            val intent = Intent(this, Cont_RNA_Activity::class.java)
            intent.putExtra("Device_address",address)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_ENABLE_BLUETOOTH){
            if(resultCode == Activity.RESULT_OK){
                if(blAdapter!!.isEnabled){
                    Toast.makeText(this,"Bluetooth ha sido Habilitdao",Toast.LENGTH_SHORT).show()
                }
                else{
                    Toast.makeText(this,"Bluetooth ha sido deshabilitado",Toast.LENGTH_SHORT).show()
                }
            } else if (resultCode == Activity.RESULT_CANCELED){
                Toast.makeText(this,"Se cancelo la habilitación del Bluetooth",Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode==777){//ya es el permiso
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                //aqui se tiene el permiso de la camara y ha sido aceptado
                abreCamara()
            }else{
                //el permiso no ha sido aceptado
                Toast.makeText(this, "Permisos Rechazados por primera vez", Toast.LENGTH_SHORT).show()
            }
        }
    }

}