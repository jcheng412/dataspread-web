import React, {Component} from 'react'
import {Dropdown, Button, Header, Icon, Modal} from 'semantic-ui-react'
import Stomp from "stompjs";


export default class ModalNewFile extends Component {
    constructor(props) {
        super(props);
        this.state = {
            //           loadModalOpen: false,
            data: null,
            BooksOptions: [],
            BooksSelected: ""
        };
        this.triggerObject = (<Button secondary fluid onClick={this.handleNew}>New File</Button>);
        /*if (props.inMenu) {
            this.triggerObject = (<Dropdown.Item onClick={this.handleOpen}>Open</Dropdown.Item>);
        } else {
            this.triggerObject = (<Button secondary fluid onClick={this.handleOpen}>Open File</Button>);
        }*/
        //this._handleLoad = this._handleLoad.bind(this);
        if (typeof process.env.REACT_APP_BASE_HOST === 'undefined') {
            this.urlPrefix = "";
            this.stompClient = Stomp.client("ws://" + window.location.host + "/ds-push/websocket");
        } else {
            this.urlPrefix = "http://" + process.env.REACT_APP_BASE_HOST;
            console.log('error?: ' + this.urlPrefix)
            this.stompClient = Stomp.client("ws://" + process.env.REACT_APP_BASE_HOST + "/ds-push/websocket");
        }
    }

    handleNew = () => {
        // fetch data from api
        fetch(this.urlPrefix + '/api/addBook')
            .then(response => response.json())
            .then(data => this.transform(data))
            .then(this.props.onSelectFile(data.value))
            .catch(() => {
                alert("Lost connection to server.")
            });

    }


    //transform data
    transform = (raw_data) => {
        let data = [];
        for (let book in raw_data) {
            let d = {
                "text": raw_data[book].text,
                "value": raw_data[book].value,
                "description": raw_data[book].description,
                "content": <Header icon='table' content={raw_data[book].text} subheader={raw_data[book].content}/>
            };
            delete d["description"];
            data.push(d)
        }
        return data
    }
}